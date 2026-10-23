# ADR-0016: Controle de concorrência otimista nos cadastros (versão e 409)

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

O requisito RNF008 pede que vários usuários possam editar ao mesmo tempo **sem perder dados**. Sem controle,
vale o "último que salva ganha": a atendente abre um transfer, a gerente abre o mesmo transfer, as duas
editam, e quem salvar por último sobrescreve a alteração da outra sem nenhum aviso.

O motivo está registrado no cabeçalho da migration `V9__Versao_Concorrencia.sql`: cada tabela editável ganha
uma coluna `versao`, todo `UPDATE` passa a conferir a versão, e se outra pessoa salvou antes a API responde
409 em vez de sobrescrever.

## Decisão

- **Coluna `versao` em todas as tabelas editáveis** (`V9`): usuários, passageiros, motoristas, veículos,
  ordens de serviço, transfers, pontos de coleta e paradas. `DEFAULT 0` preenche o que já existia.
- **`@Version` nas 8 entidades** (lock otimista do JPA): o Hibernate gera `UPDATE ... WHERE id = ? AND versao = ?`
  e soma 1 na versão.
- **A versão vai e volta pela API:** todo `*ResponseDTO` devolve `versao`, e todo `*RequestDTO` aceita
  `versao` (opcional). Se o cliente manda a versão que carregou e ela já ficou velha, o service lança
  `ConflitoDeVersaoException` antes de alterar qualquer campo.
- **Versão ausente não confere** (cliente antigo continua funcionando). Mesmo assim, duas gravações
  simultâneas continuam barradas pelo próprio `@Version` (`ObjectOptimisticLockingFailureException`).
- **As duas exceções viram 409** no `GlobalExceptionHandler`, com `erro: "Conflito de edição"`.
- **O service faz `flush()` depois de editar**, para a resposta já sair com a versão nova. Sem isso a
  próxima edição do mesmo cliente daria 409 sem motivo (registrado no cabeçalho do `VersaoCadastrosIntegrationTest`).
- **Login não conta como edição do usuário:** `Usuario.ultimoLogin` tem `@OptimisticLock(excluded = true)`.
  Sem isso, o admin que estivesse editando um usuário levaria 409 só porque a pessoa entrou no sistema
  (comentário no próprio campo).
- A conferência dos cadastros fica num lugar só, `service/Versoes.conferir`. Transfer e OS fazem a mesma
  conferência no próprio service.
- A `V10__Status_Transfer_Com_Os.sql`, que corrige status antigos, sobe a versão junto, para quem estava com
  a tela aberta receber 409 em vez de sobrescrever a correção.

## Alternativas consideradas

Não há registro de alternativas avaliadas: os commits (`8aa5e59`, `53c6648`, `b7fab76`, `231c8ec` e os de
2026-10-21) não têm corpo, e o PR #10 não tem descrição. O que o código mostra é a escolha pelo lock
otimista do JPA, sem nenhum lock pessimista (`SELECT ... FOR UPDATE`) no projeto.

## Consequências

- Duas edições sobre o mesmo dado nunca se sobrescrevem caladas: a segunda recebe 409 e precisa recarregar.
- O front precisa guardar a `versao` que recebeu e devolvê-la no PUT/PATCH para ganhar a proteção completa.
- Cada edição custa um `flush()` explícito dentro da transação.
- Toda tabela editável nova precisa da coluna `versao`, do `@Version` e da conferência no service.

## Onde encontrar no código

- Migrations: `api/src/main/resources/db/migration/V9__Versao_Concorrencia.sql`, `V10__Status_Transfer_Com_Os.sql`
- Entidades: campo `versao` com `@Version` em `model/*.java`; `@OptimisticLock(excluded = true)` em `model/Usuario.java`
- Conferência: `service/Versoes.java` (cadastros), `service/TransferService.java`, `service/OrdemServicoService.java`
- Erro: `exception/ConflitoDeVersaoException.java`, `exception/GlobalExceptionHandler.java` (409)
- Testes: `service/*ServiceTest` (casos "versão velha"), `web/*ErrosWebTest` (409),
  `integracao/VersaoCadastrosIntegrationTest.java`, `integracao/RelacionamentosIntegrationTest.java`,
  `scripts/api/test-versao.sh` (cada cadastro contra a API rodando)
