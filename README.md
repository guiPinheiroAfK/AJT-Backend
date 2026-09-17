# AJT Backend — Sistema Receptivo

**AJT Viagens e Turismo**

API REST para gestão de receptivo turístico da AJT Viagens e Turismo (antiga SOS Viale): cadastros de passageiros, motoristas, veículos e pontos de coleta, agendamento de transfers, geração de ordens de serviço, controle de acesso por perfil e relatórios.

Este backend é a evolução do sistema desktop original (`SOSViale---Sistema-Receptivo`, Java + Swing) para uma arquitetura web, com o front-end em repositório separado (Angular + Tailwind, a criar).

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 17 + Spring Boot (Web, Security, Data JPA) |
| Frontend | Angular + Tailwind CSS (repositório separado) |
| Banco de dados | PostgreSQL |
| Migrações | Flyway |
| Autenticação | JWT |
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
ajt-backend/
├── api/
│   ├── src/main/java/com/AJTBackend/
│   │   ├── config/         # segurança, JWT, CORS
│   │   ├── controller/     # controllers REST por domínio
│   │   ├── dto/            # objetos de entrada/saída da API
│   │   ├── exception/      # exceções de domínio e tratamento global de erros
│   │   ├── model/          # entidades JPA
│   │   ├── repository/     # repositórios Spring Data
│   │   └── service/        # regras de negócio
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/   # migrações Flyway
│   ├── src/tests/          # scripts de smoke test (bash)
│   └── pom.xml
├── docker-compose.yml       # PostgreSQL + API para desenvolvimento
└── .env.example
```

> Estrutura de referência — ajuste conforme o código for evoluindo.

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

A API sobe por padrão em `http://localhost:8080`. No profile `dev`, a documentação fica em `http://localhost:8080/swagger-ui.html`.

**Primeiro acesso:** usuário `admin` / senha `admin123`. O login retorna `trocarSenha: true` e a senha deve ser trocada em `PUT /api/auth/senha`.

### 4. Testes

```bash
cd api
mvn test
```

- **Unitários e de camada web** (services, JWT, criptografia, matriz de permissões, validação): rodam sem dependências externas.
- **Integração** (`integracao/*IntegrationTest`): sobem um PostgreSQL 16 real via Testcontainers e simulam a API de câmbio com WireMock. **Precisam do Docker rodando**; sem Docker são pulados.
- Cobertura: `api/target/site/jacoco/index.html` após `mvn test`.

---

## Segurança e controle de acesso

Autenticação via `Authorization: Bearer <token>`. Perfil e status do usuário são lidos do banco a cada requisição: usuário desativado ou com perfil alterado é afetado imediatamente, e trocar a senha invalida tokens antigos.

| Recurso | Leitura (GET) | Escrita (POST/PUT/PATCH) | DELETE |
|---|---|---|---|
| `usuarios` | ADMIN | ADMIN | ADMIN |
| `motoristas`, `veiculos`, `ordens-servico` | todos | ADMIN, GERENTE | ADMIN, GERENTE |
| `paradas-os` | todos | ADMIN, GERENTE (MOTORISTA: só `PATCH` de `statusParada`) | ADMIN, GERENTE |
| `passageiros`, `transfers`, `pontos-coleta` | todos | ADMIN, GERENTE, ATENDENTE | ADMIN, GERENTE |
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
- **Passageiros** — cadastro com documento (CPF, RG, CNH, Passaporte) e nacionalidade.
- **Motoristas e veículos** — frota disponível para atendimento.
- **Pontos de coleta** — locais de embarque/desembarque.
- **Transfers** — agendamento de deslocamentos, com origem, destino, horário e valor.
- **Ordens de serviço (OS)** — agrupam transfers de um motorista/veículo em um dia, com paradas.

---

## Repositórios relacionados

- **Frontend** (Angular + Tailwind): `AJT-Frontend` (link a definir)
- **Sistema legado** (Java Swing): `SOSViale---Sistema-Receptivo`

---

## Status

🚧 **Em desenvolvimento** — reescrita do sistema receptivo original para arquitetura web.
