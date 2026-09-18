# ADR-0007: Correção do N+1 nas paradas de OS

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

`ParadaOsService.toResponseDTO` monta o campo `transferIds` de cada parada
percorrendo `paradaOs.getTransfers()` — uma coleção `@ManyToMany(fetch = LAZY)`.
Sem carregamento antecipado, cada parada disparava uma query separada pra buscar
seus transfers na hora de montar a resposta: listar 20 paradas virava 1 query pra
buscar as paradas + 20 queries pra buscar os transfers de cada uma (N+1 clássico).
O problema piora linearmente com o número de paradas — não aparece em teste manual
com poucos dados, só sob volume real.

Separadamente, `ParadaOsService.buscarTransfers` (usado ao criar/atualizar uma
parada com uma lista de `transferIds`) fazia um `findById` por transfer, dentro de
um loop — outro N+1, desta vez de escrita.

## Decisão

- **Leitura**: `ParadaOsRepository` ganhou `@EntityGraph(attributePaths = "transfers")`
  nos métodos `findById` e `findByOrdemServicoIdOrderByOrdemParadaAsc` — isso
  instrui o Hibernate a trazer os transfers na mesma query (via `JOIN FETCH`),
  em vez de uma query por parada.
- **Listagem paginada** (`listarTodos`): não dá pra usar `@EntityGraph` com
  paginação numa coleção `ManyToMany` sem gerar resultado incorreto (o `JOIN`
  multiplica linhas antes da paginação). A solução foi `hibernate.default_batch_fetch_size: 50`
  no `application.yml` — em vez de uma query por parada, o Hibernate agrupa até
  50 IDs por vez numa cláusula `WHERE transfer_id IN (...)`. Não é uma query só,
  mas é O(n/50) em vez de O(n).
- **Escrita**: `buscarTransfers` passou a usar `transferRepository.findAllById(ids)`
  (uma query só) e depois compara o tamanho do resultado com o tamanho da lista
  de IDs pedida — se algum ID não voltou, é porque não existe, e a exception
  `TransferNaoEncontradoException` é lançada com o ID específico que faltou.

## Alternativas consideradas

- **`JOIN FETCH` manual em JPQL na query paginada:** tecnicamente possível com
  cuidado extra (paginação em memória em vez de no banco), mas isso anula o
  ganho de paginar no banco pra começo de conversa — descartado.
  `default_batch_fetch_size` resolve sem esse trade-off.
- **DTO projection direto via JPQL (sem carregar a entidade inteira):** mais
  performático ainda em teoria, mas exigiria reescrever a query de listagem à
  mão e perderia o reaproveitamento do mapeamento de entidade já existente.
  Ficou como otimização possível futura se o volume justificar.
- **Carregar tudo eager (`FetchType.EAGER`) na entidade:** descartado — eager
  em `@ManyToMany` carrega a coleção *sempre*, mesmo em consultas que não
  precisam dela, e é geralmente considerado anti-padrão no JPA por essa razão.

## Consequências

- `PerformanceIntegrationTest` existe especificamente pra travar essa regressão:
  ele conta as queries reais executadas (via estatísticas do Hibernate) e falha
  se o número voltar a crescer com o volume de paradas. Qualquer refatoração
  futura em `ParadaOsService`/`ParadaOsRepository` que reintroduza N+1 quebra
  esse teste.
- `default_batch_fetch_size: 50` é uma configuração global (afeta qualquer
  coleção lazy no projeto, não só paradas/transfers) — é geralmente seguro
  como padrão, mas vale lembrar que é global ao mexer em outras entidades.
- `findAllById` detecta ID inexistente comparando tamanhos de coleção, não
  identificando de forma nativa "qual" ID faltou sem esse passo extra de
  comparação — é um pouco mais código que um `findById` por item, mas troca
  N queries por 1 query + 1 comparação em memória.

## Onde encontrar no código

- `@EntityGraph`: `repository/ParadaOsRepository.java`
- `default_batch_fetch_size`: `application.yml`, `spring.jpa.properties.hibernate`
- Busca em lote na escrita: `service/ParadaOsService.java`, método `buscarTransfers`
- Teste de regressão de performance: `api/src/test/java/com/AJTBackend/integracao/PerformanceIntegrationTest.java`
- Teste da busca em lote detectando ID inexistente: `api/src/test/java/com/AJTBackend/service/ParadaOsServiceTest.java`,
  método `buscaTransfersNumaUnicaConsultaEDetectaIdInexistente`

## Atualizações posteriores

- **2026-09-18 — [ADR-0010](0010-passageiros-no-transfer-e-os-como-relacionamento.md):** a coleção
  `Transfer.passageiros` (lazy) também é carregada em lote pelo mesmo `default_batch_fetch_size: 50`, então
  listar transfers com `passageiroIds` não gera uma query por transfer.
