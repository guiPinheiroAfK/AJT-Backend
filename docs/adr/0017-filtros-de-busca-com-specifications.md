# ADR-0017: Filtros das listagens com Specifications e `%`/`_` tratados como texto

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

O RF015 pede filtros nas listagens: transfers por status, período e texto (origem ou destino), ordens de
serviço por status, período, motorista e veículo, passageiros e motoristas por parte do nome. Os filtros são
opcionais e combináveis: qualquer subconjunto pode vir na mesma requisição.

Escrever um método de repository por combinação (`findByStatusAndDataBetween...`) não escala. E o texto
digitado vai para um `LIKE`, onde `%` e `_` são curingas: o comentário do `EspecificacoesTest` registra o
risco ("um `%` digitado na busca viraria curinga e traria a base inteira").

## Decisão

- **Uma peça por tipo de filtro** em `repository/Especificacoes`: `contem`, `algumContem` (texto em pelo
  menos um dos campos), `igual`, `dataEntre` e `nenhum`. Cada peça devolve `null` quando o valor não veio, e
  o `Specification.allOf` ignora o `null`: filtro vazio é "não filtra por esse campo".
- **Os repositories filtráveis estendem `JpaSpecificationExecutor`** (transfers, OS, passageiros e
  motoristas) e o service monta a combinação com `Specification.allOf(...)`.
- **Os parâmetros chegam num record `Filtro*DTO`** por recurso, recebido com `@ParameterObject` no controller.
- **Texto sem diferenciar maiúsculas** (`lower` dos dois lados) e **`%`, `_` e `\` escapados** antes do
  `LIKE`, com `ESCAPE '\'`.
- **Período fechado nas duas pontas**, e qualquer ponta pode faltar. Período invertido (início depois do fim)
  é `400` via `Especificacoes.validarPeriodo`, em vez de uma lista vazia sem explicação (comentário do método).
- **Campo aninhado com ponto** (`"motorista.id"`), usado pela regra do motorista (ADR-0018).
- **`nenhum()`** devolve uma disjunção vazia (`WHERE 1=0`), para o motorista sem cadastro ligado não ver nada.
- Ordenação por campo inexistente continua `400` (`PropertyReferenceException`, ADR-0005).

## Alternativas consideradas

Não há registro de alternativas avaliadas: os commits (`fe0298e`, `d40c283`, `6d58465`) não têm corpo, e o
PR #12 não tem descrição. O que o código mostra é a Criteria API via Spring Data Specifications, sem
Querydsl nem SQL montado à mão.

## Consequências

- Um filtro novo é uma linha no `Filtro*DTO` e uma peça no `allOf` do service, sem método novo de repository.
- O escape do `LIKE` só é provado de verdade contra o banco: por isso existe o `FiltrosNoBancoRepositoryTest`
  (`@DataJpaTest`), além do `EspecificacoesTest` com mocks, que confere qual comparador é escolhido.
- Busca por "contém" não usa índice B-tree comum. Com o volume atual isso não foi problema; se for, o caminho
  é um índice trigram no Postgres.

## Onde encontrar no código

- Peças: `api/src/main/java/com/AJTBackend/repository/Especificacoes.java`
- Filtros: `dto/FiltroTransferDTO`, `FiltroOrdemServicoDTO`, `FiltroPassageiroDTO`, `FiltroMotoristaDTO`
- Uso: método `listar` de `service/TransferService`, `OrdemServicoService`, `PassageiroService`, `MotoristaService`
- Testes: `repository/EspecificacoesTest.java` (mocks), `repository/FiltrosNoBancoRepositoryTest.java` (banco real),
  `integracao/FiltrosEMinhasOsIntegrationTest.java` (HTTP), `scripts/api/test-filtros.sh` (API rodando)
