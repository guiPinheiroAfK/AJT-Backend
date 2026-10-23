# ADR-0021: Estratégia de testes v2: um slice por camada, nomes que dizem o cenário e given/when/then

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

A ADR-0014 definiu três níveis de teste (unitário, web e integração). Desde então, dois critérios da
avaliação do projeto passaram a pesar:

- **critério 5, isolamento de camadas:** `@WebMvcTest` para controllers e `@DataJpaTest` para repositories,
  "evitando o uso indiscriminado do `@SpringBootTest`";
- **critério 8, nomenclatura e organização:** o nome do método precisa dizer o cenário e o resultado, e o
  corpo segue BDD/AAA, sem misturar preparação, execução e verificação na mesma linha.

Em 2026-10-22 o PR #16 (Kamilly) criou o slice de repositório (`RepositorioTestBase`) e aplicou
`// Given / When / Then` na suíte, como registram as "Atualizações posteriores" da ADR-0014. Esta ADR fecha o
padrão: nomes `deve…_quando…`, comentários em minúsculo e nenhuma linha que execute e verifique ao mesmo tempo.

## Decisão

### Um contexto por camada

| Camada | Base | Contexto | Docker |
|---|---|---|:---:|
| `config/`, `service/` | nenhuma | JUnit 5 + Mockito, sem Spring | não |
| `repository/` | `RepositorioTestBase` | `@DataJpaTest` + PostgreSQL 16 (Testcontainers) | sim |
| `web/` | `WebTestBase`, `ErrosWebTestBase` | `@WebMvcTest` + Spring Security, services mockados | não |
| `integracao/` | `IntegracaoTestBase` | `@SpringBootTest` + PostgreSQL 16 + WireMock | sim |

- **`@SpringBootTest` só em `integracao/`.** Fica lá o que precisa de HTTP, segurança, banco e serviço externo
  ao mesmo tempo: autenticação ponta a ponta, câmbio com cache e timeout do Feign, auditoria na mesma transação,
  filtros e HU07 pela API.
- **`@DataJpaTest` para o que só o banco prova:** `@Query` JPQL, `@EntityGraph` contando queries, escape do
  `LIKE`, agregações do painel, documento cifrado na coluna, constraints (`UNIQUE`, FK) e o vínculo único da
  V11. Usa `@AutoConfigureTestDatabase(replace = NONE)` porque as migrations são de Postgres e um banco em
  memória esconderia justamente os erros de SQL. O `CriptografiaConverter` entra por `@Import`, porque o
  slice não escaneia `@Component`.
- **Sem `@WebMvcTest(controllers = ...)` por classe.** Todas as classes web usam a mesma configuração da
  `WebTestBase`, e o Spring reaproveita um contexto só (cache de contexto). Restringir por controller criaria
  um contexto novo por classe e deixaria a suíte mais lenta sem ganho de isolamento.
- **Sem Docker, `repository/` e `integracao/` são puladas** (`@Testcontainers(disabledWithoutDocker = true)`),
  sem quebrar o build.

### Nome e corpo de cada teste

- **Nome `deve<Resultado>_quando<Cenario>`** e **`naoDeve<Resultado>_quando<Cenario>`** no caso negativo
  (ex.: `deveRetornar404_quandoTransferNaoExiste`). Em `@ParameterizedTest`, `name = "..."` legível.
- **Três blocos separados por linha em branco, com `// given`, `// when` e `// then` em minúsculo.** Sem
  `// given` quando não há preparação.
- **O `when` é uma ação só, numa instrução própria que guarda o resultado:**
  `Throwable erro = catchThrowable(() -> ...)` para exceção e `ResultActions resposta = mockMvc.perform(...)`
  no MockMvc. Os `andExpect` e `assertThat` ficam no `then`. Por isso não se usa mais `assertThatThrownBy`
  nem `perform(...).andExpect(...)` encadeado, e o `// When / Then` da versão anterior saiu.
- **Dois cenários viram dois testes.** Setup repetido 3 vezes ou mais vira helper privado com nome claro
  (`logadoComo`, `transferBase`); a base de integração ganhou `getComo`, `postJson`, `patchJson`, `putJson`,
  `deleteComo`, `idDe` e `criarPelaApi`.
- **Asserção não se perde:** nas reescritas, uma verificação só pode ser reforçada, nunca removida.
- **Ordem fixa onde a ordem importa:** `Set.of` muda a ordem de iteração a cada execução. Nos testes do
  "id que falta" (`TransferServiceTest`, `ParadaOsServiceTest`) a lista vai num `LinkedHashSet`, senão a
  cobertura de uma linha oscila entre execuções.

### Números em 2026-10-22 (contados, não estimados)

| Pasta | Classes de teste | Métodos | Execuções |
|---|---:|---:|---:|
| `config/` | 5 | 48 | 56 |
| `service/` | 19 | 297 | 323 |
| `repository/` | 10 | 68 | 72 |
| `web/` | 12 | 159 | 363 |
| `integracao/` | 6 | 75 | 75 |
| **Total** | **52** | **647** | **889** |

`mvn verify`: 889 testes, 0 falhas, 0 pulados (com Docker). JaCoCo: 12 de 7.701 instruções e 4 de 520
branches sem cobertura (99% e 99%), igual ao da `main` antes desta mudança.

## Alternativas consideradas

- **Manter `// Given / When / Then` com `// When / Then` junto** (o padrão do PR #16): descartado porque
  `assertThatThrownBy(...)` e `perform(...).andExpect(...)` executam e verificam na mesma linha, que é o que o
  critério 8 pede para separar.
- **Nomes livres descritivos** (`transfersDoDiaSaemContadosPorStatus`): legíveis, mas sem a forma
  resultado/cenário que o critério pede. Trocados por `deve…_quando…`.
- **`@WebMvcTest(controllers = ...)` por classe:** descartado pelo custo de contexto (ver acima).
- **H2 nos testes de repositório:** descartado, as migrations são de Postgres.

## Consequências

- Cada falha aponta a camada: erro de regra quebra um unitário, de SQL quebra um `@DataJpaTest`, de contrato
  HTTP quebra um `@WebMvcTest`.
- Os nomes ficam longos, mas a lista de testes do relatório do Surefire vira a especificação do comportamento.
- Mais testes do que antes (829 para 889 execuções), porque testes com dois cenários foram divididos.
- `repository/` e `integracao/` continuam exigindo Docker.

## Onde encontrar no código

- Bases: `api/src/test/java/com/ajtbackend/repository/RepositorioTestBase.java`,
  `web/WebTestBase.java`, `web/ErrosWebTestBase.java`, `integracao/IntegracaoTestBase.java`
- Slice de repositório: `api/src/test/java/com/ajtbackend/repository/*RepositoryTest.java`
- Perfil de teste: `api/src/test/resources/application-test.yml` (`generate_statistics` para contar queries)
- [ADR-0014](0014-estrategia-de-testes-e-demonstracao.md) (os três níveis originais) e
  [ADR-0008](0008-testcontainers-singleton-e-versao-fixada.md) (container singleton)
