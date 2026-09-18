# ADR-0009: Infra — perfis dev/prod, container sem root, Postgres restrito

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

A configuração original tinha três hábitos de "ambiente de desenvolvimento"
vazando pra configuração padrão, sem distinção de ambiente:

1. **Swagger UI e `show-sql` sempre ligados** — útil em desenvolvimento, mas
   expor a documentação interativa da API (`/swagger-ui.html`) publicamente em
   produção é superfície de ataque desnecessária, e `show-sql: true` polui log
   de produção com toda query executada.
2. **Container Docker rodando como root** — o `Dockerfile` não criava usuário
   próprio; se um atacante conseguisse executar código dentro do container, teria
   privilégio de root nele.
3. **PostgreSQL exposto em `0.0.0.0:5454`** no `docker-compose.yml` — acessível
   por qualquer interface de rede da máquina, não só localhost.

## Decisão

- **Perfis Spring `dev`/`prod`** (`AJT_PROFILE`, padrão `dev`): o bloco de
  configuração de desenvolvimento (`show-sql: true`, Swagger habilitado) fica
  isolado num bloco `---` de profile `dev` no `application.yml`. A configuração
  raiz (aplicada sempre) já vem com Swagger **desabilitado** por padrão — ou
  seja, produção precisa fazer nada pra ficar segura; é desenvolvimento que
  precisa opt-in setando `AJT_PROFILE=dev` (o padrão do `.env.example` e do
  `docker-compose.yml` já é `dev`, pensando no ambiente local).
- **Container roda como usuário não-root**: `Dockerfile` cria um usuário e grupo
  `ajt` (`addgroup -S ajt && adduser -S ajt -G ajt`) e usa `USER ajt` antes do
  `ENTRYPOINT`. O JAR é copiado com `--chown=ajt:ajt`. Também foi adicionado
  `-XX:MaxRAMPercentage=75` no `ENTRYPOINT`, pra a JVM respeitar o limite de
  memória do container em vez de assumir a memória total do host.
- **Postgres restrito a localhost**: `docker-compose.yml` mudou a porta exposta
  de `"5454:5432"` para `"127.0.0.1:5454:5432"` — só processos na própria
  máquina conseguem conectar, não a rede local inteira. Também passou a usar as
  variáveis `AJT_DB_USER`/`AJT_DB_PASSWORD` do `.env` em vez de credenciais
  fixas (`postgres`/`postgres`) hardcoded no compose.
- **Variáveis obrigatórias explícitas no compose**: `AJT_JWT_SECRET` e
  `AJT_CRYPTO_KEY` usam a sintaxe `${VAR:?mensagem de erro}` — o
  `docker compose up` falha na hora, com mensagem clara, se essas variáveis
  não estiverem definidas, em vez de subir a aplicação e falhar depois de forma
  menos óbvia (ou pior, subir com uma chave padrão insegura).

## Alternativas consideradas

- **Arquivo `application-prod.yml` separado, com o `dev` sendo o padrão sem
  sufixo:** era a estrutura mais comum em outros projetos Spring, mas inverte a
  postura de segurança — o "padrão" (sem setar profile nenhum) ficaria com
  Swagger exposto. Decidido inverter: o padrão do arquivo raiz é o mais seguro
  (produção), e desenvolvimento é quem faz opt-in.
- **Não restringir a porta do Postgres**, confiando só na senha: descartado —
  "defesa em profundidade" é o princípio aqui. Mesmo com senha forte, não expor
  a porta pra rede é uma camada a mais que custa zero.
- **Usar imagem `postgres:16-alpine` em vez de `postgres:16`:** não decidido
  nesta ADR — o compose atual usa `postgres:16` (Debian-based). Fica registrado
  como possível otimização futura de tamanho de imagem, não avaliada a fundo
  ainda.

## Consequências

- Rodar localmente sem setar `AJT_PROFILE` continua funcionando com Swagger
  ligado (comportamento de desenvolvimento preservado), porque o
  `docker-compose.yml` e o `.env.example` já fixam `AJT_PROFILE=dev` como
  padrão local.
- Deploy em produção precisa **lembrar de setar `AJT_PROFILE=prod`** (ou
  qualquer valor diferente de `dev`) explicitamente — não é automático. Isso é
  uma responsabilidade operacional que vale checklist de deploy.
- `AJT_JWT_SECRET` e `AJT_CRYPTO_KEY` ausentes agora **impedem a aplicação de
  subir** (tanto por validação no código — ver ADR-0004 e ADR-0003 — quanto
  pelo compose recusando subir sem elas). Isso é intencional: falhar cedo e
  alto é preferível a subir inseguro silenciosamente.

## Onde encontrar no código

- Perfis dev/prod: `api/src/main/resources/application.yml` (bloco raiz vs.
  bloco `spring.config.activate.on-profile: dev`)
- Container sem root: `api/Dockerfile`
- Rede e variáveis obrigatórias do Postgres/app: `docker-compose.yml`
- Documentação de variáveis de ambiente: `.env.example`, `README.md`
  (seção "Configuração rápida")
