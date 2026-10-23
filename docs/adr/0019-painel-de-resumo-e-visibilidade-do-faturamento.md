# ADR-0019: Painel de resumo do dia e faturamento visível só para quem gerencia

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

A HU08 (RF012) pede um painel com o resumo do dia: quantos transfers e quantas OS em cada status, e o
faturamento. A HU é do gerente, e faturamento é informação financeira da empresa, que atendente e motorista
não precisam ver.

## Decisão

- **Uma rota, `GET /api/painel/resumo?data=`**, liberada para todos os perfis autenticados (`SecurityConfig`).
- **Consultas agregadas no banco** (`GROUP BY` e `SUM` em JPQL), em vez de buscar os registros e contar no
  front (javadoc do `PainelService`):
  - `TransferRepository.contarPorStatusNaData` e `OrdemServicoRepository.contarPorStatusNaData`;
  - `TransferRepository.somarValorBaseNoPeriodo`, com `COALESCE(..., 0)` e sem o status `CANCELADO`.
- **Todo status aparece no resumo**, com 0 nos que não tiveram registro, para o front não tratar ausência.
- **Faturamento do dia e do mês (do dia 1 até a data) só para ADMIN e GERENTE.** Para os outros perfis os
  dois campos saem `null` e as consultas de soma nem são feitas. Motivo no comentário do `PainelService`:
  "faturamento é informação financeira: só quem gerencia vê (a HU08 é do gerente)".
- **"Hoje" no fuso `America/Sao_Paulo`**, não no fuso do servidor, porque o container costuma rodar em UTC
  (comentário de `PainelService.FUSO_OPERACAO`). O mesmo fuso vale para `/minhas` (ADR-0018).

## Alternativas consideradas

Não há registro de outras alternativas avaliadas além da registrada no javadoc (contar no front). O commit
`8016d0a` não tem corpo e o PR #10 não tem descrição. O que o código mostra é a regra de perfil aplicada no
service (campo `null`), e não uma rota separada só para o faturamento.

## Consequências

- Uma requisição devolve o painel inteiro, com 2 a 4 consultas agregadas, independente do volume.
- A regra do faturamento fica no service; quem consumir o DTO precisa tratar `null` como "sem permissão",
  não como zero.
- O valor somado é o `valorBase` em reais (ADR-0006 e `ValorTransferService`): transfer em moeda estrangeira
  sem cotação no cadastro fica fora da soma.

## Onde encontrar no código

- `api/src/main/java/com/AJTBackend/controller/PainelController.java`
- `service/PainelService.java`, `dto/PainelResumoDTO.java`
- Consultas: `repository/TransferRepository.java`, `repository/OrdemServicoRepository.java`
- Testes: `service/PainelServiceTest.java` (regra de perfil e fuso), `repository/ConsultasDoPainelRepositoryTest.java`
  (consultas no banco real), `integracao/RelacionamentosIntegrationTest.java`, `scripts/api/test-painel.sh`
