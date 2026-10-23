# ADR-0018: Motorista vê só as próprias OS (vínculo usuário-motorista e `/minhas`)

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

A HU07 pede que o motorista veja as próprias ordens de serviço do dia. Até então o perfil MOTORISTA lia
qualquer OS (a matriz da ADR-0002 libera GET para todos), e não havia ligação entre o **usuário** que faz
login e o **cadastro de motorista** que aparece na OS: são tabelas diferentes (`usuarios` e `motoristas`).

## Decisão

- **Vínculo no banco** (`V11__Usuario_Motorista.sql`): `usuarios.motorista_id` aponta para `motoristas.id`.
  - `UNIQUE`: um cadastro de motorista pertence a um usuário só.
  - `ON DELETE SET NULL`: excluir o motorista não apaga o usuário, só desfaz o vínculo (ele volta a não ver
    nenhuma OS). Os dois motivos estão no cabeçalho da migration.
- **Regras do vínculo** no `UsuarioService`: só usuário de perfil MOTORISTA pode ser ligado (400 nos outros),
  o cadastro precisa existir (404) e não pode estar ligado a outro usuário (400). Quem deixa de ser MOTORISTA
  perde o vínculo.
- **Uma classe decide o acesso**, `service/AcessoMotorista`: quem não é MOTORISTA vê tudo; o MOTORISTA só vê a
  OS cujo motorista é o cadastro ligado a ele; sem vínculo, não vê nenhuma.
- **A OS de outro motorista "não existe" para ele:** busca, PDF e paradas respondem **404, não 403**, para
  ele nem ficar sabendo que o registro existe (comentário em `ParadaOsServiceTest`).
- **Listagens já saem filtradas:** a listagem de OS soma `igual("motorista.id", ...)` ou `nenhum()`
  (ADR-0017), e a de paradas usa `ParadaOsRepository.findByOrdemServicoMotoristaId`.
- **`GET /api/ordens-servico/minhas?data=`**: as OS do motorista no dia, cada uma com as paradas em ordem.
  Sem `data`, usa hoje no fuso de São Paulo (o mesmo do painel, ADR-0019). Rota só do perfil MOTORISTA
  (`SecurityConfig`); os outros recebem 403.

## Alternativas consideradas

Não há registro de alternativas avaliadas: os commits (`b79a013`, `e772d01`, `23910d8`, `9edef18`) não têm
corpo, e o PR #12 não tem descrição. O que o código mostra é o vínculo por chave estrangeira em `usuarios`, e
não, por exemplo, guardar o id do motorista dentro do token.

## Consequências

- O admin precisa ligar cada usuário MOTORISTA ao cadastro dele; sem isso, o motorista loga e não vê nada.
- A regra fica num lugar só (`AcessoMotorista`), usada por OS, paradas e PDF.
- Responder 404 em vez de 403 esconde a existência do registro, mas também esconde do próprio motorista
  quando ele digita um id errado. Trade-off aceito.

## Onde encontrar no código

- Migration: `api/src/main/resources/db/migration/V11__Usuario_Motorista.sql`
- Vínculo: `model/Usuario.java` (campo `motorista`), `service/UsuarioService.java` (`resolverMotorista`),
  `repository/UsuarioRepository.findByMotoristaId`
- Regra de acesso: `service/AcessoMotorista.java`; uso em `OrdemServicoService`, `ParadaOsService` e
  `RelatorioOrdemServicoService`
- Rota: `OrdemServicoController` (`GET /minhas`), `config/SecurityConfig.java` (só MOTORISTA)
- Testes: `service/AcessoMotoristaTest.java`, `service/ParadaOsServiceTest.java`,
  `repository/UsuarioRepositoryTest.java` (vínculo único), `integracao/FiltrosEMinhasOsIntegrationTest.java`,
  `scripts/api/test-motorista-minhas-os.sh`
