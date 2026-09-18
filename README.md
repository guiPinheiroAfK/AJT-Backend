# AJT Backend — Sistema Receptivo

**AJT Viagens e Turismo**

API REST para gestão de receptivo turístico da AJT Viagens e Turismo (antiga SOS Viale): cadastros de passageiros, motoristas, veículos e pontos de coleta, agendamento de transfers, geração de ordens de serviço, controle de acesso por perfil e relatórios.

Este backend é a evolução do sistema desktop original (`SOSViale---Sistema-Receptivo`, Java + Swing) para uma arquitetura web, com o front-end em repositório separado (`AJT-Frontend`, Angular + Tailwind).

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 17 + Spring Boot (Web, Security, Data JPA) |
| Frontend | Angular + Tailwind CSS (repositório separado) |
| Banco de dados | PostgreSQL |
| Migrações | Flyway |
| Autenticação | JWT (jjwt) + senhas com BCrypt |
| Integração externa | Spring Cloud OpenFeign (Frankfurter API, câmbio) + cache Caffeine |
| Documentação da API | OpenAPI / Swagger UI (springdoc) |
| Testes | JUnit 5, Mockito, MockMvc, Testcontainers, WireMock, JaCoCo |
| Build | Maven |
| Containerização | Docker / Docker Compose |

---

## Sobre a migração

O projeto original era uma aplicação desktop (Java Swing, arquitetura View → Service → Repository, persistência via Hibernate/JPA e modo offline com snapshot local). Nesta reescrita:

- A camada **View** (Swing) é substituída pelo **frontend Angular**, que passa a consumir a API via HTTP.
- A camada **Service** dá lugar aos services do Spring, expostos por controllers REST (`@RestController`).
- O **modelo de dados** (passageiros, motoristas, veículos, pontos de coleta, transfers, ordens de serviço, usuários/perfis) é mantido como base e evolui via migrações Flyway.
- **Autenticação** segue com JWT; perfis de usuário (`ADMIN`, `GERENTE`, `ATENDENTE`, `MOTORISTA`) continuam controlando o acesso a funcionalidades, agora via Spring Security.
- Funcionalidades específicas de desktop (modo offline com snapshot local) **não fazem parte do escopo inicial** da versão web.

---

## Estrutura do repositório

```
AJT-Backend/
├── api/
│   ├── src/main/java/com/AJTBackend/
│   │   ├── client/         # Feign Client da API de câmbio
│   │   ├── config/         # segurança, JWT, CORS, criptografia, OpenAPI
│   │   ├── controller/     # controllers REST por domínio
│   │   ├── dto/            # requests/responses (isolam as entidades)
│   │   ├── exception/      # exceções de domínio e GlobalExceptionHandler
│   │   ├── model/          # entidades JPA (+ enums)
│   │   ├── repository/     # repositórios Spring Data
│   │   └── service/        # regras de negócio e transações
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/   # migrações Flyway (V1 a V7)
│   ├── src/test/           # testes unitários, de camada web e de integração
│   └── pom.xml
├── scripts/smoke-test.sh    # dispara todas as requisições da API e confere os status
├── postman/                 # coleção Postman/Insomnia pronta para a demonstração
├── docker-compose.yml       # PostgreSQL + API
└── .env.example
```

---

## Requisitos

| Ferramenta | Versão |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| PostgreSQL | 15+ (via Docker Compose) |
| Docker | opcional, recomendado |

---

## Configuração rápida

### 1. Variáveis de ambiente

```bash
cp .env.example .env
```

Defina pelo menos:

- `AJT_JWT_SECRET` — segredo usado para assinar os tokens JWT (mínimo 32 caracteres). Gere com `openssl rand -base64 48`.
- `AJT_CRYPTO_KEY` — chave AES-256 (Base64 de 32 bytes) que cifra os documentos dos passageiros. Gere com `openssl rand -base64 32`. **Não troque depois de ter dados**: documentos já cifrados ficam ilegíveis.
- `AJT_DB_URL`, `AJT_DB_USER`, `AJT_DB_PASSWORD` — credenciais do PostgreSQL.

Opcionais: `AJT_PROFILE` (`dev` liga Swagger e SQL no log; use `prod` em produção), `AJT_CORS_ORIGINS` (padrão `http://localhost:4200`), `AJT_LOGIN_MAX_TENTATIVAS` / `AJT_LOGIN_BLOQUEIO_MINUTOS`.

> A aplicação **não sobe** se `AJT_JWT_SECRET` ou `AJT_CRYPTO_KEY` estiverem ausentes ou fracos.

### 2. Banco de dados

```bash
docker compose up -d
```

As migrações Flyway rodam automaticamente na subida da aplicação.

### 3. Executar a aplicação

```bash
mvn spring-boot:run
```

O Maven **não lê o arquivo `.env`**: exporte as variáveis antes de subir (ou configure-as na Run Configuration da IDE).

```bash
# Git Bash / Linux / macOS
set -a && source .env && set +a && cd api && mvn spring-boot:run
```

```powershell
# PowerShell
Get-Content .env | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }
cd api; mvn spring-boot:run
```

A API sobe por padrão em `http://localhost:8080`. Para subir **tudo em containers** (banco + API): `docker compose up -d --build` (API em `http://localhost:9090`).

### Documentação interativa (Swagger)

Com o profile `dev` (padrão local): **http://localhost:8080/swagger-ui.html**. Faça `POST /api/auth/login`, copie o `token`, clique em **Authorize** e cole. Todos os endpoints têm resumo, exemplos de corpo e as respostas 400/401/403/404/409 documentadas.

**Primeiro acesso:** usuário `admin` / senha `admin123`. O login retorna `trocarSenha: true` e a senha deve ser trocada em `PUT /api/auth/senha`.

### 4. Testes

```bash
cd api
mvn test
```

- **Unitários e de camada web** (services, JWT, criptografia, matriz de permissões, validação): rodam sem dependências externas.
- **Integração** (`integracao/*IntegrationTest`): sobem um PostgreSQL 16 real via Testcontainers e simulam a API de câmbio com WireMock. **Precisam do Docker rodando**; sem Docker são pulados.
- Cobertura: `api/target/site/jacoco/index.html` após `mvn test`.

**Teste ponta a ponta contra a API rodando** (usa o login do admin e apaga o que criar):

```bash
AJT_PASS=<senha do admin> bash scripts/smoke-test.sh
```

**Demonstração no Postman/Insomnia:** importe `postman/AJT-Backend.postman_collection.json`. Rode *Autenticação → Login* primeiro: o token e os ids criados ficam salvos em variáveis para as requisições seguintes.

---

## Segurança e controle de acesso

Autenticação via `Authorization: Bearer <token>`. Perfil e status do usuário são lidos do banco a cada requisição: usuário desativado ou com perfil alterado é afetado imediatamente, e trocar a senha invalida tokens antigos.

| Recurso | Leitura (GET) | Escrita (POST/PUT/PATCH) | DELETE |
|---|---|---|---|
| `usuarios` | ADMIN | ADMIN | ADMIN |
| `motoristas`, `veiculos`, `ordens-servico` | todos | ADMIN, GERENTE | ADMIN, GERENTE |
| `paradas-os` | todos | ADMIN, GERENTE (MOTORISTA: só `PATCH` de `statusParada`) | ADMIN, GERENTE |
| `passageiros`, `transfers`, `pontos-coleta` | todos | ADMIN, GERENTE, ATENDENTE | ADMIN, GERENTE |
| `auditoria` (somente leitura) | ADMIN, GERENTE | — | — |
| `cotacao`, `auth/me`, `auth/senha` | todos | todos | — |

Outras proteções: bloqueio de login após tentativas erradas (HTTP 429), documento do passageiro cifrado com AES-256-GCM, CORS restrito às origens configuradas, container rodando sem root.

---

## Contrato da API para o front

- **Login:** `POST /api/auth/login` → `{ token, tipo, expiraEm (segundos), username, role, trocarSenha }`
- **Sessão:** `GET /api/auth/me` devolve o usuário logado (use ao recarregar a página).
- **Troca de senha:** `PUT /api/auth/senha` `{ senhaAtual, novaSenha }` → devolve um **token novo** (o antigo deixa de valer).
- **Listagens paginadas:** `GET /api/{recurso}?page=0&size=20&sort=campo,asc` (máx. `size=100`). Resposta:
  ```json
  { "conteudo": [], "pagina": 0, "tamanho": 20, "totalElementos": 0, "totalPaginas": 0, "primeira": true, "ultima": true }
  ```
  Vale para `GET /api/{recurso}`, `GET /api/transfers/buscar`, `GET /api/ordens-servico/buscar` e `GET /api/passageiros/buscar`. Listas "filhas" (`/paradas-os/ordem-servico/{id}`, `/pontos-coleta/transfer/{id}`) continuam como array simples.
- **Passageiros do transfer:** `passageiroIds` no corpo do transfer (POST/PUT/PATCH) e na resposta. Ausente = não altera; `[]` = remove todos. Dados completos em `GET /api/transfers/{id}/passageiros`.
- **Auditoria:** `GET /api/auditoria?tabela=transfers|ordens_servico` (paginado, mais recente primeiro; ADMIN e GERENTE).
- **Enums:**
  - `role`: `ADMIN`, `GERENTE`, `ATENDENTE`, `MOTORISTA`
  - `status` do transfer: `AGUARDANDO_OS`, `CONFIRMADO`, `EM_ANDAMENTO`, `CONCLUIDO`, `CANCELADO`
  - `status` da OS: `ABERTA`, `EM_ANDAMENTO`, `FINALIZADA`, `CANCELADA`
  - `statusParada`: `PENDENTE`, `EM_ANDAMENTO`, `CONCLUIDA`, `CANCELADA`
  - `acao` da parada: `EMBARQUE`, `DESEMBARQUE`
- **Datas/horas:** ISO-8601 sem fuso (`2026-09-20`, `14:30:00`, `2026-09-20T14:30:00`).
- **Erros:** sempre `{ timestamp, status, erro, mensagem, detalhes[] }`. Códigos: 400 (validação/regra de negócio), 401 (token ausente, inválido ou expirado: redirecionar ao login), 403 (sem permissão), 404, 409 (registro em uso), 429 (login bloqueado), 502 (API de câmbio fora do ar).

---

## Domínio principal

- **Usuários e perfis** — `ADMIN`, `GERENTE`, `ATENDENTE`, `MOTORISTA`, com controle de acesso por perfil.
- **Passageiros** — cadastro com documento (cifrado no banco) e nacionalidade.
- **Motoristas e veículos** — frota disponível para atendimento.
- **Transfers** — deslocamentos com origem, destino, horário, valor (convertido automaticamente de moeda estrangeira) e **passageiros**.
- **Pontos de coleta** — locais de embarque ordenados dentro de um transfer.
- **Ordens de serviço (OS)** — agrupam transfers de um motorista/veículo em um dia, com **paradas** que atendem vários transfers.
- **Auditoria** — trilha de quem criou, alterou (inclusive mudança de status) ou removeu transfers e OS.

### Relacionamentos do modelo

| Relação | Tipo | Onde |
|---|---|---|
| OS → Motorista, OS → Veículo | `@ManyToOne` | `OrdemServico` |
| Transfer → OS | `@ManyToOne` (opcional: "aguardando OS") | `Transfer` |
| Transfer ↔ Passageiro | `@ManyToMany` (`transfer_passageiros`) | `Transfer` |
| Ponto de coleta → Transfer | `@ManyToOne` | `PontoColeta` |
| Parada → OS | `@ManyToOne` | `ParadaOs` |
| Parada ↔ Transfer | `@ManyToMany` (`parada_os_transfers`) | `ParadaOs` |
| Motorista/Veículo/OS → coleções | `@OneToMany` (lado inverso) | entidades |

---

## Repositórios relacionados

- **Frontend** (Angular 18 + Tailwind): `AJT-Frontend`
- **Sistema legado** (Java Swing): `SOSViale---Sistema-Receptivo`

---

## Status

Entrega final da disciplina de Back-end (Spring Boot). Decisões de arquitetura e o "porquê" de cada uma ficam na branch `documentacao` (`docs/adr`).

**Limitações conhecidas:** o backend apenas *sinaliza* `trocarSenha: true` (quem força a tela de troca é o front); o contador de tentativas de login fica em memória (com várias instâncias, migrar para Redis).
