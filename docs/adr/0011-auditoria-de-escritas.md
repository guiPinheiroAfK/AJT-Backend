# ADR-0011: Auditoria das operações de escrita (transfers e ordens de serviço)

- **Status:** Aceito
- **Data:** 2026-09-18

## Contexto

A migration `V3__Create_Auditoria.sql` criou a tabela `logs_transfers` com um comentário de intenção:
"HU11 / RF014 — populada pela camada de serviço (não por trigger de banco)". Mas nada a populava. O único
rastro do que acontecia no sistema eram logs SLF4J, que têm três limitações para responder "quem mexeu nesse
transfer e quando?":

- vão para arquivo/console e se perdem na rotação de logs;
- não são consultáveis pela API (o gerente não acessa o servidor);
- são texto livre, sem vínculo estruturado com o registro afetado.

Além disso a própria tabela tinha um defeito: `id SERIAL` e `registro_id INT` (32 bits), enquanto todas as
demais tabelas do projeto usam `BIGINT` (a V2 já corrigira isso para as tabelas operacionais).

## Decisão

### Trilha gravada pela camada de serviço

Uma tabela de auditoria consultável, alimentada explicitamente pelos services (como a V3 já previa):

- Entidade `LogAuditoria` ↔ tabela `logs_transfers`: `tabela_afetada`, `registro_id`, `mensagem`, `data_hora`.
- `AuditoriaService.registrar(tabela, id, acao)` grava a linha. A mensagem já inclui **quem** fez:
  `"Transfer criado: GRU -> Hotel (por maria)"`. O usuário vem de `UsuarioLogado` (ADR-0013); sem usuário
  autenticado vira `"sistema"`.
- `AuditoriaService.registrarAtualizacao(tabela, id, acao, statusAnterior, statusAtual)` monta a mensagem de
  atualização e, **se o status mudou**, diz de qual para qual: `"Transfer atualizado: status AGUARDANDO_OS
  -> CONFIRMADO (por maria)"`.

### O que é auditado

| Recurso | Eventos | `tabela_afetada` |
|---|---|---|
| Transfer | criado, atualizado (PUT/PATCH, com mudança de status), removido | `transfers` |
| Ordem de serviço | criada, atualizada (com mudança de status), removida | `ordens_servico` |

Foram escolhidos os dois recursos com **ciclo de vida de status**, onde "quem mudou de EM_ANDAMENTO para
CONCLUIDO" é uma pergunta operacional real. Cadastros de apoio (motoristas, veículos, passageiros) não são
auditados por enquanto.

### Mesma transação da operação

`registrar` é chamado **dentro** da transação de gravação de quem o chama (o método é `@Transactional` com
propagação padrão, então participa da transação existente). Consequência: se a operação de negócio der
rollback, o registro de auditoria some junto; nunca sobra log de algo que não aconteceu.

### Consulta e permissão

- `GET /api/auditoria?tabela=transfers|ordens_servico&page=&size=`, paginado (`PaginaResponseDTO`), mais
  recente primeiro (`sort = dataHora desc` por padrão). Sem `tabela` devolve tudo.
- Somente leitura: não existe POST/PUT/DELETE de auditoria. A trilha só pode ser escrita pelos services.
- **Somente ADMIN e GERENTE** (`SecurityConfig`). Ver "Atualizações" no ADR-0002.

### Migration V7

`V7__Auditoria_Bigint.sql`: `id` e `registro_id` → `BIGINT`, a sequência passa a `AS BIGINT` e é criado o
índice `idx_logs_tabela_data (tabela_afetada, data_hora DESC)`, exatamente o padrão de acesso da consulta.
Foi criada uma migration nova (e não editado o `V3`) porque uma migration já aplicada é imutável: alterar o
arquivo quebraria o checksum do Flyway em qualquer banco existente.

## Alternativas consideradas

- **Trigger no PostgreSQL.** Captura tudo, inclusive alterações fora da aplicação, mas não sabe **quem** é o
  usuário da aplicação (só o usuário do banco) e esconde regra em SQL fora do código versionado e testado.
  A própria V3 já decidia contra ("não por trigger").
- **Hibernate Envers / `@EntityListeners` automáticos.** Auditam qualquer campo sem código repetido, mas
  produzem tabelas `_aud` por entidade e diffs de campo, mais do que a pergunta "quem mudou o status" exige.
  É a evolução natural se a auditoria precisar cobrir todos os campos de todas as entidades.
- **Só logs SLF4J (já existiam).** Continuam para diagnóstico técnico, mas não substituem uma trilha
  consultável pela API e ligada ao registro.
- **Gravar a auditoria em transação separada (`REQUIRES_NEW`).** Garantiria log mesmo se a operação
  falhasse, mas registraria eventos que não ocorreram. Descartado: para auditoria de negócio o
  comportamento correto é "aconteceu ⇒ está no log".

## Consequências

- Cada escrita em transfer/OS agora faz **um `INSERT` a mais** na mesma transação. Custo pequeno e aceito.
- A `mensagem` é texto (legível para pessoas), não um diff estruturado. Não dá para reconstruir o estado
  anterior de um registro a partir dela; ela responde "quem, o quê, quando", não "qual era o valor antes".
- A tabela só cresce. Não há política de retenção/limpeza; se o volume justificar, o índice por
  `(tabela_afetada, data_hora)` já suporta uma rotina de expurgo por data.
- O nome da tabela (`logs_transfers`) ficou histórico: ela guarda auditoria de mais de um recurso. Renomear
  exigiria nova migration e foi considerado custo sem benefício funcional.
- Quem adicionar um novo recurso auditável deve chamar `AuditoriaService` **no service**, nunca no
  controller, para manter a garantia da transação compartilhada.

## Onde encontrar no código

- Entidade e repositório: `model/LogAuditoria.java`, `repository/LogAuditoriaRepository.java`
- Regra: `service/AuditoriaService.java`
- Uso: `service/TransferService.java` (`criar`, `atualizar`, `atualizarParcial`, `deletar`) e
  `service/OrdemServicoService.java` (mesmos quatro métodos)
- Consulta: `controller/AuditoriaController.java`, `dto/AuditoriaResponseDTO.java`
- Permissão: `config/SecurityConfig.java` (regra `/api/auditoria/**`)
- Migration: `db/migration/V7__Auditoria_Bigint.sql` (tabela original em `V3__Create_Auditoria.sql`)
- Testes: `service/AuditoriaServiceTest.java` (mensagem, usuário, "sistema", filtro),
  `integracao/RelacionamentosIntegrationTest.java` (`auditoriaRegistraCriacaoEMudancaDeStatus...`,
  `auditoriaNaoSobraQuandoAOperacaoFalha`), `web/AutorizacaoWebTest.java` (só ADMIN/GERENTE leem)
