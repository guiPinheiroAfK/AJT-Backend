# ADR-0008: Testcontainers — container singleton e versão fixada

- **Status:** Aceito
- **Data:** 2026-09-17

## Contexto

Os testes de integração (`IntegracaoTestBase` e suas 4 subclasses) sobem um
PostgreSQL real e um servidor WireMock via Testcontainers. Duas coisas quebraram
na prática ao rodar em Windows com Docker Desktop:

1. **Versão do Testcontainers incompatível com Docker Desktop no Windows.**
   A versão gerenciada pelo Spring Boot 3.4.5 (`1.20.6`) usa um cliente de named
   pipe que recebia uma resposta vazia/malformada da API do Docker Desktop nesse
   ambiente — os testes de integração simplesmente eram pulados (o Testcontainers
   detecta "Docker indisponível" e desabilita a classe de teste via
   `@Testcontainers(disabledWithoutDocker = true)`) mesmo com o Docker rodando
   normalmente (confirmado testando `docker run hello-world` com sucesso).
2. **Container e WireMock reiniciando entre classes de teste.** `POSTGRES` e
   `COTACAO_API` eram campos `static` anotados com `@Container` /
   `@RegisterExtension` numa classe base (`IntegracaoTestBase`), herdada por 4
   classes de teste. O JUnit 5, por padrão, para um recurso `static` anotado
   assim no `afterAll` de **cada classe** que o usa — e como o campo é
   compartilhado (mesma variável estática), a segunda classe de teste via o
   recurso "parado" e o reiniciava, só que numa porta nova. O contexto Spring da
   primeira classe (cacheado entre testes pelo próprio Spring, pra performance)
   continuava configurado com a porta antiga, e todas as classes depois da
   primeira falhavam com `CannotCreateTransaction` ao tentar falar com o banco.

## Decisão

- **Fixar a versão do Testcontainers em `1.21.4`** via propriedade
  `testcontainers.version` no `pom.xml` — o Spring Boot expõe essa propriedade
  justamente pra permitir sobrescrever a versão gerenciada por ele sem precisar
  declarar cada dependência de Testcontainers manualmente.
- **Padrão "singleton container"** (documentado oficialmente pelo próprio
  Testcontainers pra esse exato cenário): os campos `POSTGRES` e `COTACAO_API`
  deixaram de ter as anotações `@Container`/`@RegisterExtension` e passaram a
  ser iniciados manualmente num bloco `static { }` na classe base. Sem essas
  anotações, o JUnit não gerencia o ciclo de vida — o recurso sobe uma vez, na
  primeira vez que a classe é carregada pela JVM, e só morre quando os testes
  terminam (limpo automaticamente pelo Ryuk do Testcontainers, que registra o
  container pra remoção independente de anotação).

## Alternativas consideradas

- **Expor o daemon do Docker via TCP sem TLS** (`tcp://localhost:2375`), um
  workaround comum pra esse tipo de incompatibilidade de named pipe no Windows:
  descartado porque abre a API do Docker sem autenticação na máquina — qualquer
  processo local passaria a poder controlar containers. Resolvido de forma mais
  segura só atualizando a versão da biblioteca.
- **Um container por classe de teste (sem compartilhar):** resolveria o
  problema de restart-com-porta-nova (cada classe teria o seu, do início ao
  fim), mas cada container do Postgres leva alguns segundos pra subir — com 4
  classes de teste, isso somaria um tempo de execução bem maior. O padrão
  singleton mantém um único container pra toda a suíte.
- **`@DirtiesContext` pra forçar o Spring a não cachear o contexto entre
  classes:** trataria o sintoma (contexto desatualizado), mas não a causa
  (container reiniciando sem necessidade) — e pioraria o tempo de execução, já
  que cada classe recriaria o contexto Spring inteiro do zero.

## Consequências

- Os 4 test classes de integração compartilham o mesmo Postgres e o mesmo
  WireMock — dados gravados por uma classe teoricamente poderiam ser vistos por
  outra se não houver cuidado. Na prática cada teste usa dados isolados (IDs
  gerados via `UUID`, usuários com username aleatório), mas é um ponto de
  atenção pra quem for escrever testes de integração novos aqui.
- Fixar a versão do Testcontainers manualmente significa que, quando o Spring
  Boot for atualizado no futuro, essa propriedade deve ser revisada — pode ser
  que a versão gerenciada pelo Spring já tenha corrigido o bug do npipe, e a
  fixação manual deixe de ser necessária (ou passe a fixar uma versão mais
  antiga que a gerenciada, o que seria um retrocesso).
- Sem Docker disponível, `@Testcontainers(disabledWithoutDocker = true)`
  continua pulando essas classes normalmente (isso não dependia do bug —
  continua funcionando igual).

## Onde encontrar no código

- Configuração da versão: `api/pom.xml`, propriedade `testcontainers.version`
- Padrão singleton: `api/src/test/java/com/AJTBackend/integracao/IntegracaoTestBase.java`
  (bloco `static { POSTGRES.start(); COTACAO_API.start(); }`, com o comentário
  explicando o porquê de não usar `@Container`/`@RegisterExtension`)
- As 4 classes que herdam desse padrão:
  `api/src/test/java/com/AJTBackend/integracao/AutenticacaoIntegrationTest.java`,
  `PersistenciaIntegrationTest.java`, `PerformanceIntegrationTest.java`,
  `CotacaoIntegrationTest.java`
