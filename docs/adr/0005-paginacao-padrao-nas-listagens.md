# ADR-0005: Paginação padrão em todas as listagens

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

Todo endpoint de listagem (`GET /api/transfers`, `/api/motoristas`, `/api/usuarios`
etc.) fazia `findAll()` sem limite e devolvia um array com a tabela inteira. Com
poucos registros isso não é problema, mas cresce sem limite — e o front não tinha
como pedir "só os 20 mais recentes", nem havia contrato de ordenação.

## Decisão

Todo `listarTodos` e as buscas por filtro (`buscarPorStatus`, `buscarPorNacionalidade`)
passaram a receber um `Pageable` (do Spring Data) e devolver um `PaginaResponseDTO<T>`
— um envelope próprio, não o `Page` do Spring Data direto, porque o JSON serializado
do `PageImpl` do Spring não tem contrato garantido entre versões (já mudou de formato
entre versões do Spring Data no passado). O formato fica estável:

```json
{
  "conteudo": [...],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 57,
  "totalPaginas": 3,
  "primeira": true,
  "ultima": false
}
```

Parâmetros aceitos via query string: `?page=0&size=20&sort=campo,asc`. Tamanho
padrão 20, máximo 100 (`spring.data.web.pageable.max-page-size`) — pedir mais que
isso trunca em 100 em vez de dar erro, isso é comportamento padrão do Spring Data.

As listas "filhas" (`GET /api/paradas-os/ordem-servico/{id}`,
`GET /api/pontos-coleta/transfer/{id}`) **não** foram paginadas — na prática o
número de paradas de uma OS ou pontos de um transfer é sempre pequeno (dezenas,
não milhares), então paginar ali seria complexidade sem benefício real.

## Alternativas consideradas

- **Cursor-based pagination (keyset pagination):** mais eficiente pra tabelas
  muito grandes (evita `OFFSET` caro em páginas distantes), mas mais complexo de
  implementar e de consumir no front. Descartado por enquanto — o volume de dados
  do projeto não justifica essa complexidade ainda. Fica como candidato se algum
  dia uma tabela crescer a ponto do `OFFSET` virar gargalo real.
- **Devolver o `Page` do Spring Data direto, sem DTO próprio:** mais rápido de
  implementar, mas descartado pelo motivo já citado (formato instável entre
  versões, e exposição de detalhes internos do Spring Data no contrato de API).
- **Paginar via header (`X-Total-Count`) em vez de envelope no corpo:** é um
  padrão usado por outras APIs, mas exigiria o front ler metadados de paginação
  de um lugar diferente dos dados — o envelope no corpo mantém tudo junto numa
  única resposta JSON, mais simples de consumir.

## Consequências

- **Quebra de contrato**: todo consumidor de listagem precisou mudar de "leio um
  array" pra "leio `.conteudo`". Isso foi comunicado no doc de handoff pro front
  (fora desta pasta — foi um artifact avulso, não faz parte do histórico de ADR).
- Front pode pedir ordenação (`sort=campo,asc`) sem precisar de endpoint
  específico pra isso — mas pedir ordenação por um campo que não existe na
  entidade agora dá `400` em vez de erro genérico (ver tratamento de
  `PropertyReferenceException` no `GlobalExceptionHandler`).
- Volume de dados trafegado por requisição cai (20 registros por vez em vez da
  tabela inteira), o que ajuda tanto o backend (menos serialização) quanto o
  front (menos dado pra renderizar de uma vez).

## Onde encontrar no código

- DTO do envelope: `dto/PaginaResponseDTO.java`
- Uso nos services: qualquer `listarTodos(Pageable pageable)` — por exemplo,
  `service/TransferService.java`, `service/UsuarioService.java`
- Configuração de tamanho padrão/máximo: `application.yml`,
  `spring.data.web.pageable`
- Tratamento de ordenação inválida: `exception/GlobalExceptionHandler.java`,
  método `handleOrdenacaoInvalida`
- Testes: `api/src/test/java/com/AJTBackend/web/ValidacaoWebTest.java`, método
  `paginacaoTemPadraoETamanhoMaximo`, e contra banco real
  `api/src/test/java/com/AJTBackend/integracao/PersistenciaIntegrationTest.java`,
  métodos `paginacaoRealComTotais` e `ordenarPorCampoInexistenteRetorna400`

## Atualizações posteriores

- **2026-09-18 — [ADR-0011](0011-auditoria-de-escritas.md):** `GET /api/auditoria` também devolve
  `PaginaResponseDTO`, com ordenação padrão `dataHora` decrescente (a trilha mais recente primeiro).
