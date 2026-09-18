# ADR-0006: Cache e isolamento transacional da cotação de câmbio

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

Ao criar/atualizar um transfer em moeda estrangeira, o `TransferService` chamava a
API externa de câmbio (Frankfurter, via Feign) **dentro** da mesma transação que
gravava o transfer no banco. Dois problemas:

1. A chamada HTTP (até 3s de connect + 5s de read, pelo `feign.client.config`)
   prendia uma conexão do pool do banco por todo esse tempo, mesmo sem precisar
   dela enquanto espera a rede.
2. Toda criação de transfer em moeda estrangeira batia na API externa de novo,
   mesmo que a cotação do dia já tivesse sido consultada minutos antes — e a
   Frankfurter atualiza a cotação uma vez por dia.
3. Além disso, o timeout configurado em `feign.client.config` **nunca era
   aplicado**: a partir do Spring Cloud OpenFeign 4.x, esse prefixo de
   configuração mudou pra `spring.cloud.openfeign.client.config` — o
   `application.yml` antigo usava o prefixo antigo, então o Feign rodava sem
   timeout nenhum (dependendo só do timeout padrão da JVM, bem mais alto).

## Decisão

- **Cache de 30 minutos** (`@Cacheable` com Caffeine, `spring.cache.caffeine.spec`)
  no `CotacaoService.obterCotacao`, com chave por par de moedas. TTL de 30 min é
  uma escolha pragmática: curto o suficiente pra não ficar dias desatualizado,
  longo o suficiente pra evitar bater na API repetidamente no mesmo dia útil.
  Exceções (API fora do ar) não entram no cache — não queremos "lembrar" uma
  falha por 30 minutos.
- **Chamada HTTP fora da transação**: `TransferService.criar/atualizar` calculam
  o `valorBase` (o que pode envolver chamar a cotação) **antes** de abrir a
  transação, usando `TransactionTemplate` só pra a parte de gravação no banco.
  O método continua anotado como `@Transactional(propagation = NOT_SUPPORTED)`
  pra garantir que não há transação aberta durante essa etapa.
- **Correção do prefixo do Feign**: `application.yml` passou a usar
  `spring.cloud.openfeign.client.config`, restaurando o timeout de 3s/5s de
  verdade.
- **Falha na cotação não trava o cadastro**: se a API de câmbio estiver fora do
  ar, `TransferService` grava o transfer com `valorBase = null` em vez de falhar
  a operação inteira — o valor pode ser preenchido manualmente depois. Isso já
  existia antes desta ADR e foi mantido.

## Alternativas consideradas

- **Cron job que atualiza a cotação uma vez por dia numa tabela própria:**
  resolveria o problema de repetição de chamada de forma mais "correta"
  (cotação vira dado local, não depende de disponibilidade da API no momento
  do cadastro), mas é mais infraestrutura (job agendado, tabela nova) pra um
  ganho que o cache simples já cobre bem o suficiente hoje.
- **Cache mais longo (24h):** cogitado, já que a API só atualiza 1x/dia, mas
  descartado por não saber exatamente que horário a Frankfurter atualiza — 30
  min é conservador o bastante pra não ficar preso a uma cotação de ontem por
  muito tempo caso a atualização aconteça no meio do dia (fuso diferente).
- **Manter chamada dentro da transação, só adicionando timeout:** resolveria o
  problema do timeout (item 3), mas não o de prender conexão do pool nem o de
  chamar a API repetidamente — descartado por resolver só parte do problema.

## Consequências

- Primeira consulta de um par de moedas no dia é lenta (chamada real à API);
  as próximas 30 minutos são instantâneas (cache).
- Se a cotação mudar de verdade dentro da janela de 30 min, o sistema usa a
  cotação em cache mesmo assim — é uma imprecisão aceita conscientemente, não
  um bug.
- Conexões do pool de banco não ficam mais presas esperando rede — libera o
  pool mais rápido sob carga.
- Qualquer código futuro que precise chamar `CotacaoClient` ou `CotacaoService`
  dentro de uma transação deve seguir o mesmo padrão (calcular fora, gravar
  dentro) — é o padrão a copiar, não uma regra genérica do Spring.

## Onde encontrar no código

- Cache: `service/CotacaoService.java`, anotação `@Cacheable`
- Configuração do cache: `application.yml`, `spring.cache`
- Separação de transação: `service/TransferService.java`, métodos `criar`,
  `atualizar`, `atualizarParcial` (usam `TransactionTemplate` em vez de deixar o
  `@Transactional` do método cobrir tudo)
- Prefixo correto do Feign: `application.yml`, `spring.cloud.openfeign.client.config`
- Cliente Feign: `client/CotacaoClient.java`
- Testes: `api/src/test/java/com/AJTBackend/service/CotacaoServiceTest.java` e,
  contra WireMock simulando a API real,
  `api/src/test/java/com/AJTBackend/integracao/CotacaoIntegrationTest.java`
  (métodos `consultaApiExternaUmaVezEUsaCacheNasSeguintes` e
  `apiLentaRespeitaTimeoutERetorna502` provam o cache e o timeout de verdade)

## Atualizações posteriores

- **2026-09-18 — [ADR-0013](0013-organizacao-do-codigo-e-tamanho-das-classes.md):** a regra que decide o
  `valorBase` (respeitar o informado, BRL, converter, devolver `null` se a API cair) foi **extraída** de
  `TransferService` para `service/ValorTransferService`. O comportamento descrito nesta ADR (chamada fora
  da transação, cache de 30 min, cadastro não trava) é o mesmo. Na seção "Onde encontrar no código", leia
  `ValorTransferService.calcularValorBase` onde se lê o cálculo dentro de `TransferService`; este último
  agora só orquestra (calcula antes, grava dentro do `TransactionTemplate`). Testes: `ValorTransferServiceTest`.
