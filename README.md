# AJT Backend · Sistema Receptivo

**AJT Viagens e Turismo**

API REST para gestão de receptivo turístico da AJT Viagens e Turismo (antiga SOS Viale): cadastros de passageiros, motoristas, veículos e pontos de coleta, agendamento de transfers, ordens de serviço com roteiro de paradas, painel do dia, relatório em PDF, controle de acesso por perfil e auditoria.

Este backend é a evolução do sistema desktop original (`SOSViale---Sistema-Receptivo`, Java + Swing) para uma arquitetura web, com o front-end em repositório separado (`AJT-Frontend`, Angular + Tailwind).

| Indicador | Situação atual |
|---|---|
| Testes | 914 execuções, 0 falhas (unitários, repository, web e integração) |
| Cobertura (JaCoCo) | 100% de linhas e de branches |
| SonarQube | Quality Gate aprovado: 0 bugs, 0 vulnerabilidades, 0 code smells, 0% de duplicação, notas A |
| Banco | PostgreSQL 16, migrations Flyway V1 a V11 |

---

## Sumário

1. [Stack](#stack)
2. [Passo a passo: do zero até a API rodando](#passo-a-passo-do-zero-até-a-api-rodando)
3. [Testes e cobertura](#testes-e-cobertura)
4. [Análise no SonarQube](#análise-no-sonarqube)
5. [Scripts de verificação, smoke test e Postman](#scripts-de-verificação-smoke-test-e-postman)
6. [Tudo em containers (Docker Compose)](#tudo-em-containers-docker-compose)
7. [Problemas comuns](#problemas-comuns)
8. [Estrutura do repositório](#estrutura-do-repositório)
9. [Segurança e controle de acesso](#segurança-e-controle-de-acesso)
10. [Contrato da API para o front](#contrato-da-api-para-o-front)
11. [Domínio principal](#domínio-principal)
12. [Documentação de arquitetura](#documentação-de-arquitetura)

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 17 + Spring Boot 3.4 (Web, Security, Data JPA, Validation) |
| Frontend | Angular + Tailwind CSS (repositório separado) |
| Banco de dados | PostgreSQL 16 |
| Migrações | Flyway |
| Autenticação | JWT (jjwt) + senhas com BCrypt |
| Integração externa | Spring Cloud OpenFeign (Frankfurter API, câmbio) + cache Caffeine |
| Relatórios | OpenPDF (PDF da ordem de serviço) |
| Documentação da API | OpenAPI / Swagger UI (springdoc) |
| Testes | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers, WireMock, Awaitility, JaCoCo |
| Qualidade | SonarQube |
| Build | Maven |
| Containerização | Docker / Docker Compose |

---

## Passo a passo: do zero até a API rodando

### 0. Pré-requisitos

| Ferramenta | Versão | Para quê |
|---|---|---|
| JDK | 17 ou mais novo | compilar e rodar a API |
| Maven | 3.8+ | build e testes (o projeto não tem `mvnw`) |
| Docker Desktop | qualquer recente | banco local, testes com Testcontainers e SonarQube |
| Git Bash | vem com o Git for Windows | rodar os scripts `.sh` no Windows |
| jq | qualquer | só para o `scripts/smoke-test.sh` |

Confira o Java antes de tudo: `java -version` tem que mostrar 17 ou mais. Se aparecer `1.8`, aponte o `JAVA_HOME` para um JDK 17 e coloque `%JAVA_HOME%\bin` na frente do `PATH`.

### 1. Configurar o `.env`

Na raiz do repositório:

```bash
cp .env.example .env
```

Preencha pelo menos:

- `AJT_JWT_SECRET`: segredo que assina os tokens JWT, com no mínimo 32 caracteres.
- `AJT_CRYPTO_KEY`: chave AES-256 (Base64 de 32 bytes) que cifra o documento dos passageiros. **Não troque depois de ter dados**: documentos já cifrados ficam ilegíveis.

Para gerar os dois:

```bash
# Git Bash / Linux / macOS
openssl rand -base64 48   # AJT_JWT_SECRET
openssl rand -base64 32   # AJT_CRYPTO_KEY
```

```powershell
# PowerShell (sem openssl): rode duas vezes, com 48 e com 32
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Os outros valores do `.env.example` já servem para rodar local: banco em `localhost:5454`, usuário e senha `postgres`, perfil `dev` (Swagger ligado e SQL no log) e CORS liberado para `http://localhost:4200`. Opcionais: `AJT_LOGIN_MAX_TENTATIVAS` e `AJT_LOGIN_BLOQUEIO_MINUTOS`.

> A aplicação **não sobe** se `AJT_JWT_SECRET` ou `AJT_CRYPTO_KEY` estiverem ausentes ou fracos.

### 2. Subir o banco

Com o Docker Desktop aberto, na raiz do repositório:

```bash
docker compose up -d postgres
```

O PostgreSQL fica em `localhost:5454` (só na própria máquina). Para conferir: `docker ps` deve mostrar `ajt-postgres` como `healthy`. As migrations Flyway rodam sozinhas quando a API sobe.

### 3. Rodar o back-end

O Maven **não lê o `.env`**, então as variáveis precisam ser carregadas antes. Escolha um dos jeitos:

**PowerShell**

```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }
cd api
mvn spring-boot:run
```

**Git Bash / Linux / macOS**

```bash
set -a && source .env && set +a
cd api && mvn spring-boot:run
```

**IntelliJ**

1. Abra o projeto no IntelliJ (o `pom.xml` fica em `api/`).
2. Em *Run → Edit Configurations → AJTBackendApplication*, cole as variáveis do `.env` em **Environment variables** (formato `CHAVE=valor;CHAVE=valor`).
3. Rode a classe `com.ajtbackend.AJTBackendApplication`.

A API sobe em **http://localhost:8080**.

### 4. Primeiro acesso

- **Swagger** (perfil `dev`): **http://localhost:8080/swagger-ui.html**. Faça `POST /api/auth/login`, copie o `token`, clique em **Authorize** e cole. Cada endpoint tem resumo, exemplos de corpo e as respostas de erro documentadas.
- **Usuário inicial:** `admin` / `admin123` (criado pela migration `V5__Seed_Admin.sql`). O login devolve `trocarSenha: true`; troque a senha em `PUT /api/auth/senha`.

---

## Testes e cobertura

Com o Docker Desktop aberto:

```bash
cd api
mvn clean verify
```

Resultado esperado: `Tests run: 914, Failures: 0, Errors: 0, Skipped: 0` e `BUILD SUCCESS`.

| Nível | Onde | O que prova | Precisa de Docker? |
|---|---|---|:---:|
| Unitário | `config/`, `service/` | regra de negócio isolada, com Mockito | não |
| Repository | `repository/*RepositoryTest` (`@DataJpaTest`) | consultas JPQL, constraints e escape do `LIKE` contra um PostgreSQL 16 real | sim |
| Web | `web/` (`@WebMvcTest`) | status HTTP, validação, matriz de permissões e formato de erro | não |
| Integração | `integracao/*IntegrationTest` (`@SpringBootTest`) | aplicação inteira com banco real (Testcontainers) e câmbio simulado (WireMock) | sim |

- Sem Docker, os testes de repository e integração são **pulados** (não falham).
- Todo teste segue o padrão `deve<Resultado>_quando<Cenario>` com blocos `// given`, `// when` e `// then`.
- **Relatório de cobertura:** abra `api/target/site/jacoco/index.html` no navegador depois do `mvn clean verify`.
- **O que cada teste cobre**, ligado aos requisitos e aos status HTTP: [docs/catalogo-de-testes.md](docs/catalogo-de-testes.md).

Para rodar só uma camada:

```bash
mvn test -Dtest='com.ajtbackend.service.*Test'
mvn test -Dtest='com.ajtbackend.web.*Test'
mvn test -Dtest='com.ajtbackend.integracao.*Test'
```

---

## Análise no SonarQube

O SonarQube roda local, num container. A cobertura vem do relatório do JaCoCo gerado pelo `mvn clean verify`.

**1. Subir o SonarQube** (só na primeira vez; depois é `docker start ajt-sonar`):

```bash
docker run -d --name ajt-sonar -p 9000:9000 sonarqube:community
```

Espere 1 a 2 minutos e abra **http://localhost:9000**. O login inicial é `admin` / `admin`, e ele pede para trocar a senha no primeiro acesso.

**2. Gerar o token:** em *My Account → Security → Generate Token*, escolha o tipo *Global Analysis* (ou *Project Analysis* do projeto `ajt-backend`) e copie o valor. O token não aparece de novo.

**3. Rodar testes + análise** (dentro de `api/`):

```powershell
# PowerShell
$env:SONAR_TOKEN = '<seu token>'
mvn clean verify
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar "-Dsonar.host.url=http://localhost:9000" "-Dsonar.projectKey=ajt-backend" "-Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml"
```

```bash
# Git Bash / Linux / macOS
export SONAR_TOKEN=<seu token>
mvn clean verify
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.projectKey=ajt-backend \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

**4. Ver o resultado:** o fim do log mostra `ANALYSIS SUCCESSFUL` e o painel fica em **http://localhost:9000/dashboard?id=ajt-backend** (o servidor leva alguns segundos para processar).

Observações:

- Cada container do SonarQube tem o próprio banco, com usuários e tokens próprios. Um token criado num container não funciona em outro.
- A edição Community não exporta PDF. Para guardar o painel, use *Ctrl+P → Salvar como PDF* no navegador, com "Gráficos de fundo" marcado.
- A edição Community não faz a análise de injeção (SQL injection, XSS) das edições pagas.
- Nunca coloque o token no repositório.

---

## Scripts de verificação, smoke test e Postman

Os três rodam **contra a API já no ar** (passo 3 ou Docker Compose). Usam o admin (`AJT_USER`, padrão `admin`) e a senha `AJT_PASS` (padrão `admin123`); se a senha foi trocada, passe a nova.

**Scripts por recurso** (`scripts/api/`, só `bash` + `curl`, rodam no Git Bash): cada `test-*.sh` cobre um recurso, caminho feliz e triste (400/401/403/404/409), cria dados com sufixo único e apaga tudo no fim, então pode rodar duas vezes seguidas. O `run-all.sh` roda todos, mostra o resumo e sai com código 1 se algum falhar.

```bash
bash scripts/api/run-all.sh                                        # API local em http://localhost:8080
AJT_PASS=<senha do admin> bash scripts/api/run-all.sh              # se a senha do admin já foi trocada
AJT_URL=http://localhost:9090 bash scripts/api/run-all.sh          # API do Docker Compose
bash scripts/api/test-permissoes.sh                                # um recurso só
```

Scripts disponíveis: `test-auth`, `test-usuarios`, `test-motoristas`, `test-veiculos`, `test-passageiros`, `test-transfers`, `test-pontos-coleta`, `test-paradas-os`, `test-ordens-servico`, `test-filtros`, `test-versao`, `test-permissoes`, `test-motorista-minhas-os`, `test-painel`, `test-auditoria` e `test-cotacao` (este depende da API de câmbio externa).

**Smoke test** (a sequência da demonstração, 70 verificações; precisa de `jq`):

```bash
AJT_PASS=<senha do admin> bash scripts/smoke-test.sh
```

**Postman / Insomnia:** importe `postman/AJT-Backend.postman_collection.json` e rode *Autenticação → Login* primeiro. O token e os ids criados ficam salvos em variáveis para as requisições seguintes. O request "Trocar senha" muda a senha do admin de verdade.

> Scripts, smoke test e coleção criam e apagam registros: não aponte para um banco de produção.

---

## Tudo em containers (Docker Compose)

Sobe banco + API + front. Precisa do `.env` preenchido (passo 1) e do repositório **AJT-Frontend** clonado ao lado deste (`../AJT-Frontend`; outro caminho vai em `AJT_FRONT_DIR` no `.env`).

```bash
docker compose up -d --build
```

| Serviço | Endereço |
|---|---|
| Front (nginx) | http://localhost:8081 (`AJT_FRONT_PORT` muda a porta) |
| API | http://localhost:9090 |
| PostgreSQL | `localhost:5454` (só na própria máquina) |

O nginx do front repassa `/api` para a API dentro da rede do compose, então o navegador fala com uma origem só e o CORS não entra em jogo.

```bash
docker compose up -d --build postgres app   # só banco + API, sem o front
docker compose logs -f app                  # acompanhar o log da API
docker compose down                         # parar tudo (os dados do banco ficam no volume ajt_data)
```

A imagem da API é multi-stage (build com Maven, runtime só com o JRE) e roda sem root.

---

## Problemas comuns

| Sintoma | Causa provável | Como resolver |
|---|---|---|
| `Could not resolve placeholder 'AJT_JWT_SECRET'` | o `.env` não foi carregado | carregue as variáveis (passo 3) ou configure na Run Configuration da IDE |
| `invalid target release: 17` ou `UnsupportedClassVersionError` | Java 8 no `PATH` | aponte `JAVA_HOME` para um JDK 17 e coloque o `bin` dele na frente do `PATH` |
| IntelliJ não acha `com.AJTBackend...` ou marca tudo em vermelho | o pacote virou `com.ajtbackend` (minúsculo) | *File → Invalidate Caches → Invalidate and Restart* e, se precisar, `mvn clean` |
| `Connection refused` na porta 5454 | banco não subiu | abra o Docker Desktop e rode `docker compose up -d postgres` |
| testes de repository/integração aparecem como pulados | Docker desligado | abra o Docker Desktop e rode de novo |
| `run-all.sh` ou smoke dizem que não conseguiram logar | senha do admin trocada ou API fora do ar | passe `AJT_PASS=<senha>` e confira `AJT_URL` |
| token do Sonar com `{"valid":false}` | token de outro container do SonarQube | gere um token no container que está na porta 9000 |
| porta 9000 ocupada ao subir o Sonar | outro container do SonarQube ligado | `docker ps` e `docker stop <nome>` no que não for usar |

---

## Estrutura do repositório

```
AJT-Backend/
├── api/
│   ├── src/main/java/com/ajtbackend/
│   │   ├── client/         # Feign Client da API de câmbio
│   │   ├── config/         # segurança, JWT, CORS, criptografia, OpenAPI
│   │   ├── controller/     # controllers REST por domínio (inclui painel e PDF da OS)
│   │   ├── dto/            # requests/responses e filtros das listagens
│   │   ├── exception/      # exceções de domínio e GlobalExceptionHandler
│   │   ├── model/          # entidades JPA (+ enums)
│   │   ├── repository/     # repositórios Spring Data e Specifications
│   │   └── service/        # regras de negócio, transações, auditoria e PDF
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/   # migrações Flyway (V1 a V11)
│   ├── src/test/java/com/ajtbackend/
│   │   ├── config/ service/    # unitários
│   │   ├── repository/         # @DataJpaTest contra PostgreSQL real
│   │   ├── web/                # @WebMvcTest
│   │   └── integracao/         # @SpringBootTest + Testcontainers + WireMock
│   ├── Dockerfile
│   └── pom.xml
├── scripts/
│   ├── smoke-test.sh        # sequência da demonstração, confere os status
│   └── api/                 # um script por recurso (test-*.sh) + common.sh + run-all.sh
├── postman/                 # coleção Postman/Insomnia pronta para a demonstração
├── docs/                    # ADRs, mapa do código e catálogo de testes
├── docker-compose.yml       # PostgreSQL + API + front (nginx)
└── .env.example
```

---

## Segurança e controle de acesso

Autenticação via `Authorization: Bearer <token>`. Perfil e status do usuário são lidos do banco a cada requisição: usuário desativado ou com perfil alterado é afetado na hora, e trocar a senha invalida os tokens antigos.

| Recurso | Leitura (GET) | Escrita (POST/PUT/PATCH) | DELETE |
|---|---|---|---|
| `usuarios` | ADMIN | ADMIN | ADMIN |
| `motoristas`, `veiculos`, `ordens-servico` | todos | ADMIN, GERENTE | ADMIN, GERENTE |
| `ordens-servico/minhas` | só MOTORISTA | n/a | n/a |
| `paradas-os` | todos | ADMIN, GERENTE (MOTORISTA: só `PATCH` de `statusParada`) | ADMIN, GERENTE |
| `passageiros` | ADMIN, GERENTE, ATENDENTE | ADMIN, GERENTE, ATENDENTE | ADMIN, GERENTE |
| `transfers`, `pontos-coleta` | todos | ADMIN, GERENTE, ATENDENTE | ADMIN, GERENTE |
| `auditoria` (somente leitura) | ADMIN, GERENTE | n/a | n/a |
| `painel/resumo` | todos (faturamento só para ADMIN e GERENTE) | n/a | n/a |
| `cotacao`, `auth/me`, `auth/senha` | todos | todos | n/a |

Regras extras:

- O **MOTORISTA só enxerga as próprias ordens de serviço** (e as paradas delas): as OS cujo motorista é o cadastro ligado ao usuário dele (`usuarios.motorista_id`, migration V11).
- O MOTORISTA não lista passageiros (o cadastro tem documento); os passageiros da OS dele chegam por `GET /api/transfers/{id}/passageiros`, com o documento mascarado.

Outras proteções: bloqueio de login após tentativas erradas (HTTP 429), documento do passageiro cifrado com AES-256-GCM, CORS restrito às origens configuradas, CSRF desligado de propósito por ser uma API stateless com token Bearer (ADR-0015), container rodando sem root.

---

## Contrato da API para o front

- **Login:** `POST /api/auth/login` → `{ token, tipo, expiraEm (segundos), username, role, trocarSenha }`
- **Sessão:** `GET /api/auth/me` devolve o usuário logado (use ao recarregar a página).
- **Troca de senha:** `PUT /api/auth/senha` `{ senhaAtual, novaSenha }` → devolve um **token novo** (o antigo deixa de valer).
- **Listagens paginadas:** `GET /api/{recurso}?page=0&size=20&sort=campo,asc` (máx. `size=100`). Resposta:
  ```json
  { "conteudo": [], "pagina": 0, "tamanho": 20, "totalElementos": 0, "totalPaginas": 0, "primeira": true, "ultima": true }
  ```
  Listas "filhas" (`/paradas-os/ordem-servico/{id}`, `/pontos-coleta/transfer/{id}`) continuam como array simples.
- **Filtros nas listagens** (query string, todos opcionais e somados entre si; `%` e `_` são tratados como texto):
  - `GET /api/transfers`: `status`, `dataInicio`, `dataFim`, `busca` (texto em origem/destino), `osId`, `semOs=true`
  - `GET /api/ordens-servico`: `status`, `dataInicio`, `dataFim`, `motoristaId`, `veiculoId`
  - `GET /api/motoristas`: `nome`, `cnh`
  - `GET /api/passageiros`: `nome`, `nacionalidade`
- **Edição simultânea:** toda resposta de cadastro traz `versao`. Mande a `versao` que a tela carregou no `PUT`/`PATCH`; se outra pessoa salvou antes, a API responde **409** em vez de sobrescrever. Sem `versao` no corpo, a conferência não é feita.
- **Passageiros do transfer:** `passageiroIds` no corpo do transfer (POST/PUT/PATCH) e na resposta. Ausente = não altera; `[]` = remove todos. Dados completos em `GET /api/transfers/{id}/passageiros`.
- **OS do dia do motorista:** `GET /api/ordens-servico/minhas?data=AAAA-MM-DD` (sem `data`, usa hoje).
- **Painel:** `GET /api/painel/resumo?data=AAAA-MM-DD` → transfers e OS por status e faturamento do dia (sem `data`, usa hoje no fuso de São Paulo).
- **PDF da OS:** `GET /api/ordens-servico/{id}/pdf` → `application/pdf` com motorista, veículo, roteiro de paradas e transfers com passageiros (sem valores nem documentos). Abre inline no navegador.
- **Auditoria:** `GET /api/auditoria?tabela=transfers|ordens_servico` (paginado, mais recente primeiro; ADMIN e GERENTE).
- **Enums:**
  - `role`: `ADMIN`, `GERENTE`, `ATENDENTE`, `MOTORISTA`
  - `status` do transfer: `AGUARDANDO_OS`, `CONFIRMADO`, `EM_ANDAMENTO`, `CONCLUIDO`, `CANCELADO`
  - `status` da OS: `ABERTA`, `EM_ANDAMENTO`, `FINALIZADA`, `CANCELADA`
  - `statusParada`: `PENDENTE`, `EM_ANDAMENTO`, `CONCLUIDA`, `CANCELADA`
  - `acao` da parada: `EMBARQUE`, `DESEMBARQUE`
- **Datas/horas:** ISO-8601 sem fuso (`2026-09-20`, `14:30:00`, `2026-09-20T14:30:00`).
- **Erros:** sempre `{ timestamp, status, erro, mensagem, detalhes[] }`. Códigos: 400 (validação, regra de negócio ou registro duplicado), 401 (token ausente, inválido ou expirado: redirecionar ao login), 403 (sem permissão), 404, 409 (registro em uso ou versão desatualizada), 429 (login bloqueado), 502 (API de câmbio fora do ar).

---

## Domínio principal

- **Usuários e perfis:** `ADMIN`, `GERENTE`, `ATENDENTE`, `MOTORISTA`, com controle de acesso por perfil; o usuário MOTORISTA pode ser ligado a um cadastro de motorista.
- **Passageiros:** cadastro com documento (cifrado no banco) e nacionalidade.
- **Motoristas e veículos:** frota disponível para atendimento.
- **Transfers:** deslocamentos com origem, destino, horário, valor (convertido automaticamente de moeda estrangeira) e **passageiros**. Ao entrar numa OS, o transfer passa a `CONFIRMADO`.
- **Pontos de coleta:** locais de embarque ordenados dentro de um transfer.
- **Ordens de serviço (OS):** agrupam os transfers de um motorista/veículo em um dia, com **paradas** que atendem vários transfers, e geram o PDF para o motorista.
- **Painel:** resumo do dia com transfers e OS por status e faturamento.
- **Auditoria:** trilha de quem criou, alterou (inclusive status e valor) ou removeu transfers e OS.

### Relacionamentos do modelo

| Relação | Tipo | Onde |
|---|---|---|
| OS → Motorista, OS → Veículo | `@ManyToOne` | `OrdemServico` |
| Transfer → OS | `@ManyToOne` (opcional: "aguardando OS") | `Transfer` |
| Transfer ↔ Passageiro | `@ManyToMany` (`transfer_passageiros`) | `Transfer` |
| Ponto de coleta → Transfer | `@ManyToOne` | `PontoColeta` |
| Parada → OS | `@ManyToOne` | `ParadaOs` |
| Parada ↔ Transfer | `@ManyToMany` (`parada_os_transfers`) | `ParadaOs` |
| Usuário → Motorista | `@OneToOne` (opcional) | `Usuario` |
| Motorista/Veículo/OS → coleções | `@OneToMany` (lado inverso) | entidades |

---

## Sobre a migração do sistema desktop

O projeto original era uma aplicação desktop (Java Swing, arquitetura View → Service → Repository, persistência via Hibernate/JPA e modo offline com snapshot local). Nesta reescrita:

- A camada **View** (Swing) é substituída pelo **frontend Angular**, que consome a API via HTTP.
- A camada **Service** dá lugar aos services do Spring, expostos por controllers REST (`@RestController`).
- O **modelo de dados** é mantido como base e evolui via migrações Flyway.
- **Autenticação** segue com JWT; os perfis continuam controlando o acesso, agora via Spring Security.
- Funcionalidades específicas de desktop (modo offline com snapshot local) **não fazem parte do escopo** da versão web.

---

## Documentação de arquitetura

- [docs/README.md](docs/README.md): índice das decisões de arquitetura (ADRs 0000 a 0022) e ordem de leitura sugerida.
- [docs/mapa-do-codigo.md](docs/mapa-do-codigo.md): onde está cada coisa e o checklist para adicionar recursos, campos e regras.
- [docs/catalogo-de-testes.md](docs/catalogo-de-testes.md): o que cada teste cobre, ligado aos requisitos.

As ADRs são escritas na branch eterna `documentacao` e entram na `main` periodicamente.

---

## Repositórios relacionados

- **Frontend** (Angular + Tailwind): `AJT-Frontend`
- **Sistema legado** (Java Swing): `SOSViale---Sistema-Receptivo`

---

## Status

Entrega final da disciplina de Back-end (Spring Boot).

**Limitações conhecidas:** o backend apenas *sinaliza* `trocarSenha: true` (quem força a tela de troca é o front); o contador de tentativas de login fica em memória (com várias instâncias, migrar para Redis).
