# ADR-0014: Estratégia de testes em três níveis e artefatos de demonstração

- **Status:** Aceito
- **Data:** 2026-09-18

## Contexto

O projeto precisa de confiança em três coisas diferentes, e um único tipo de teste não cobre as três:

1. **A lógica está certa?** (cálculo de valor, travas de autoproteção, regras de perfil);
2. **A API se comporta como prometido?** (status HTTP, validação, permissões, formato de erro);
3. **As peças funcionam juntas com um banco de verdade?** (mapeamento JPA, migrations, criptografia,
   constraints, performance de queries, integração externa).

Além disso, a entrega inclui uma **demonstração ao vivo** (Postman/Insomnia), que precisa ser repetível e
não depender de improviso.

## Decisão

### Três níveis de teste (149 testes)

| Nível | Ferramentas | O que prova | Quantidade | Roda sem Docker? |
|---|---|---|---:|:---:|
| **Unitário** | JUnit 5 + Mockito | regra de negócio isolada, sem Spring | 81 | sim |
| **Camada web** | `@WebMvcTest` + MockMvc + Spring Security Test | status HTTP, validação, matriz de permissões, CORS, formato de erro (services mockados) | 43 | sim |
| **Integração** | `@SpringBootTest` + Testcontainers (PostgreSQL 16) + WireMock | banco real com Flyway V1–V7, criptografia, FKs, paginação, N+1, cache/timeout do Feign, auditoria | 25 | **não** (pula sem Docker) |

Decisões de desenho:

- **Regra pesada vai para o nível mais barato que a prova.** A conversão de moeda (`ValorTransferService`)
  tem 8 testes unitários e nenhum precisa de Spring nem banco.
- **A matriz de permissões é um único teste parametrizado** (`@CsvSource`, um caso por linha) em
  `AutorizacaoWebTest`: alterar uma regra de acesso sem atualizar a tabela quebra o build.
- **Integração só onde só o banco real prova.** Ex.: junção `transfer_passageiros`, `ON DELETE CASCADE`,
  documento realmente cifrado no banco, contagem de queries (regressão de N+1), e que a migration V7
  casa com as entidades (`ddl-auto: validate`).
- **Container único para toda a suíte de integração** (padrão *singleton*, ADR-0008), pois subir um
  Postgres por classe multiplicaria o tempo.
- **Toda classe de teste documenta a si mesma** com o bloco *o que testa / como rodar / por que existe*.
- **Cobertura** medida com JaCoCo (`api/target/site/jacoco/index.html`); serve de indicador, não de meta.

### Artefatos de demonstração e verificação (versionados)

| Artefato | Para quê |
|---|---|
| `postman/AJT-Backend.postman_collection.json` | 75 requisições em 12 pastas, na ordem da demonstração. O login grava o token e cada "Criar" grava o id numa variável usada pelos seguintes. Importa no Postman **e** no Insomnia. Inclui pasta de erros (400/403/404/409) para mostrar o tratamento global. |
| `scripts/smoke-test.sh` | dispara as mesmas requisições, em sequência, contra a API **já rodando**, confere o status de cada uma e apaga o que criou (70 verificações). Configurável por `AJT_URL`, `AJT_USER`, `AJT_PASS`. |
| Swagger UI (ADR-0012) | exploração interativa e documentação viva. |

O smoke test foi executado contra três ambientes com o mesmo resultado (70/70): backend local, banco com
dados reais migrando V6→V7, e o **stack completo em containers** (`docker compose up --build`).

## Alternativas consideradas

- **Só testes de integração (tudo com banco).** Dão mais confiança por teste, mas a suíte ficaria lenta,
  dependente de Docker e ruim para diagnosticar (um erro de regra viraria um erro de contexto Spring).
- **Só testes unitários com mocks.** Rápidos, mas não enxergam SQL, migrations, tipos de coluna nem
  `@Transactional`, que foram justamente onde apareceram os bugs reais (ex.: checksum de migration, porta do
  container reiniciada, N+1).
- **Coleção Postman com testes/asserts embutidos (Newman no CI).** Tecnicamente possível; foi preferido o
  `smoke-test.sh` (bash + curl + jq) por não exigir instalar Node/Newman e por rodar em qualquer máquina
  do time. Os scripts *pré/pós-request* da coleção só encadeiam token e ids.
- **Manter os scripts antigos de `tests/`.** Removidos: usavam senha fixa, esperavam respostas em array
  (antes da paginação) e cobriam só 5 recursos; ficaram substituídos pelo smoke test.

## Consequências

- **Integração exige Docker.** Sem ele, 25 testes são pulados (não falham). No Windows com Docker Desktop
  recente foi necessário fixar o Testcontainers em 1.21.4 (ADR-0008).
- O **smoke test altera dados** (cria e apaga registros de teste) e usa o login do ADMIN; não deve ser
  apontado para um banco de produção.
- A coleção do Postman **depende de ordem** (usa ids criados pelas requisições anteriores) e o request
  "Trocar senha" muda a senha do admin de verdade; isso está avisado na descrição da coleção.
- Toda funcionalidade nova deve chegar com testes no nível adequado; o checklist está em
  `docs/mapa-do-codigo.md`.

## Onde encontrar no código

- Unitários: `api/src/test/java/com/AJTBackend/service/*Test.java`, `config/*Test.java`
- Camada web: `api/src/test/java/com/AJTBackend/web/` (`WebTestBase`, `AutorizacaoWebTest`, `ValidacaoWebTest`)
- Integração: `api/src/test/java/com/AJTBackend/integracao/` (`IntegracaoTestBase` + 5 classes);
  perfil de teste em `api/src/test/resources/application-test.yml`
- Cobertura: plugin `jacoco-maven-plugin` em `api/pom.xml`
- Demonstração: `postman/` e `scripts/smoke-test.sh` (na raiz do repositório)
- Como rodar tudo: seção *Testes* do `README.md` do projeto
