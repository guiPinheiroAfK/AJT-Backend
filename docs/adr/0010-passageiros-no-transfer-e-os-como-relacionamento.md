# ADR-0010: Passageiros no transfer (N:N) e ordem de serviço como relacionamento (N:1)

- **Status:** Aceito
- **Data:** 2026-09-18

## Contexto

O sistema é um receptivo turístico: o objetivo de um *transfer* é levar **passageiros** de um ponto a outro.
Mesmo assim, o modelo tinha duas fragilidades:

1. **Transfer sem passageiros.** A migration `V2__Create_Operacao.sql` já criava a tabela de junção
   `transfer_passageiros`, mas nenhuma entidade JPA a usava. Na prática era esquema morto: a API não tinha
   como dizer quem viaja em cada transfer, e uma pergunta básica do negócio ("quem está neste transfer?")
   não tinha resposta.
2. **`Transfer.osId` como `Long` solto.** Todas as outras ligações do modelo eram relacionamentos JPA de
   verdade (`OrdemServico → Motorista`, `ParadaOs → OrdemServico`...). Só o transfer guardava o id da OS
   como número cru, o que obrigava o service a fazer `existsById` "na mão" e escondia a relação do modelo.

## Decisão

### Transfer ↔ Passageiro: `@ManyToMany`

`Transfer` passou a ter `Set<Passageiro> passageiros`, mapeado com `@ManyToMany` + `@JoinTable` sobre a
tabela que já existia (`transfer_passageiros`, colunas `transfer_id` e `passageiro_id`). Nenhuma migration
foi necessária. A relação é **unidirecional** (só o `Transfer` conhece os passageiros): o lado inverso não
tem uso no negócio e só criaria mais um caminho para lazy loading acidental.

Contrato da API (aditivo, o front antigo continua funcionando):

| Onde | Campo / rota | Comportamento |
|---|---|---|
| Corpo de `POST`, `PUT`, `PATCH /api/transfers` | `passageiroIds: [1, 2]` | vincula esses passageiros |
| Resposta de qualquer leitura de transfer | `passageiroIds` | ids dos passageiros vinculados |
| `PUT` **sem** `passageiroIds` | — | **mantém** os passageiros atuais |
| `PATCH`/`PUT` com `passageiroIds: []` | — | remove todos os vínculos |
| `GET /api/transfers/{id}/passageiros` | rota nova | dados completos dos passageiros (o transfer traz só os ids) |

Regras de validação no `TransferService`:

- os ids são buscados com **uma única query** (`findAllById`); se o tamanho do resultado for menor que o
  pedido, algum id não existe e a operação falha com `404` citando o id que faltou (mesma técnica do ADR-0007);
- o limite é de 50 passageiros por transfer (`@Size(max = 50)` no DTO);
- a busca acontece **dentro** da transação de gravação, então se algum passageiro não existir nada é gravado.

### Transfer → OrdemServico: `@ManyToOne`

`Transfer.osId` (coluna `os_id`) virou `@ManyToOne(fetch = LAZY) OrdemServico ordemServico`, com o lado
inverso `@OneToMany List<Transfer> transfers` em `OrdemServico`. A coluna e a chave estrangeira do banco não
mudaram. **O contrato JSON também não**: o DTO continua recebendo e devolvendo `osId`; a conversão entre id
e entidade é feita no service (`buscarOrdemServico`). Sem `osId` o transfer fica "aguardando OS", como antes.

## Alternativas consideradas

- **Relação bidirecional (`Passageiro.transfers`).** Descartada: nenhuma tela ou regra precisa "de quais
  transfers este passageiro participa" por navegação de objetos, e o lado inverso convida a percorrer
  coleções lazy fora de transação. Se essa consulta virar necessária, é uma query no repositório.
- **Entidade de junção explícita (`TransferPassageiro` com campos extras).** É o desenho certo se a
  relação precisar de atributos próprios (assento, hora de embarque, status de presença). Hoje a junção
  não tem colunas além das duas chaves, então `@ManyToMany` simples é suficiente. **Se surgirem atributos na
  junção, esta ADR deve ser substituída por uma nova**, migrando para entidade de junção.
- **Endpoints dedicados `POST /transfers/{id}/passageiros/{pid}`.** Mais "RESTful puro", mas exigiria
  vários round-trips para montar um transfer com 4 passageiros e complicaria o front. Mandar
  `passageiroIds` no próprio corpo mantém a operação atômica.
- **Deixar `osId` como `Long` e só documentar.** Descartada por inconsistência com o restante do modelo e
  porque impedia navegar `os.getTransfers()`.

## Consequências

- **Exclusão de passageiro remove só o vínculo.** A FK de `transfer_passageiros` tem `ON DELETE CASCADE`
  (V2): apagar um passageiro que está em transfers **não** dá erro 409, apenas some da lista do transfer.
  Isso é intencional (o passageiro deixou de existir) e está coberto por teste de integração
  (`excluirPassageiroVinculadoRemoveSoOVinculo`). Difere de motorista/veículo, que dão 409 se estiverem
  numa OS.
- **N+1 controlado.** Montar `passageiroIds` acessa a coleção lazy de cada transfer. Numa listagem
  paginada isso não vira uma query por transfer porque o `hibernate.default_batch_fetch_size: 50` (ADR-0007)
  agrupa os carregamentos em lote.
- `PUT` "manter se ausente" é uma **decisão de compatibilidade**: um cliente antigo que edita o transfer
  sem conhecer `passageiroIds` não apaga vínculos sem querer. O preço é que `PUT` deixa de ser 100%
  "substituição total" para esse campo específico, e isso está descrito no Swagger.
- Mudanças em transfers agora geram trilha de auditoria (ADR-0011).

## Onde encontrar no código

- Entidades: `model/Transfer.java` (campos `ordemServico` e `passageiros`), `model/OrdemServico.java`
  (`transfers`)
- Regra de vínculo: `service/TransferService.java`, métodos `buscarPassageiros` e `buscarOrdemServico`
- Consulta dos passageiros do transfer: `repository/PassageiroRepository.java` (`findByTransferId`),
  `service/PassageiroService.java` (`listarPorTransfer`), `controller/TransferController.java`
  (`GET /{id}/passageiros`)
- Contrato: `dto/TransferRequestDTO.java` e `dto/TransferResponseDTO.java` (campo `passageiroIds`)
- Tabela e chaves: `db/migration/V2__Create_Operacao.sql` (`transfer_passageiros`)
- Testes: `service/TransferServiceTest.java` (vínculo em uma consulta, id inexistente, PUT mantém, `[]` limpa),
  `integracao/RelacionamentosIntegrationTest.java` (tabela de junção real, endpoint, exclusão de passageiro,
  FK da OS)
