# ADR-0020: Relatório em PDF da ordem de serviço

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

A HU10 (RF013) pede o relatório da OS para entregar **impresso** ao motorista: dados da OS, motorista,
veículo, roteiro (paradas em ordem) e os transfers com os passageiros.

## Decisão

- **`GET /api/ordens-servico/{id}/pdf`** devolve `application/pdf` com `Content-Disposition: inline`
  (`ordem-servico-{id}.pdf`): o navegador abre o PDF numa aba, e dali dá para imprimir ou baixar (comentário
  no `OrdemServicoController`).
- **Gerado no backend com OpenPDF** (`com.github.librepdf:openpdf` 1.3.42), em memória, sem arquivo em disco.
- **De propósito não entram valores nem documentos dos passageiros**, porque o papel fica com o motorista e
  os documentos são dados cifrados (RNF007). Os dois motivos estão no javadoc do
  `RelatorioOrdemServicoService`.
- **Campo vazio não vira "null" impresso** nem derruba a geração: OS sem motorista, veículo, paradas ou
  transfers sai com avisos ("Não definido", "Nenhuma parada cadastrada").
- **A mesma regra de acesso da OS** (ADR-0018): o motorista só gera o PDF das próprias OS; nas outras recebe 404.

## Alternativas consideradas

Não há registro de alternativas avaliadas (o commit `a25a6ec` não tem corpo e o PR #10 não tem descrição).
O que o código mostra é a geração no backend, e não no front a partir do JSON.

## Consequências

- O PDF é igual para qualquer cliente (front, Postman), e a regra do que não pode sair impresso fica no backend.
- Uma dependência a mais (OpenPDF), com versão fixa no `pom.xml`.
- Os testes leem o texto do PDF gerado (`PdfTextExtractor`) para conferir o que sai e o que não sai impresso.

## Onde encontrar no código

- `api/src/main/java/com/AJTBackend/service/RelatorioOrdemServicoService.java`
- Rota: `controller/OrdemServicoController.java` (`relatorioPdf`)
- Dependência: `api/pom.xml` (`openpdf`)
- Testes: `service/RelatorioOrdemServicoServiceTest.java`, `integracao/RelacionamentosIntegrationTest.java`
  (PDF de verdade, sem valor nem documento), `scripts/api/test-ordens-servico.sh` (status e `Content-Type`)
