#!/usr/bin/env bash
# o que testa: CRUD de /api/transfers (POST, GET lista, GET id, GET buscar por status, PUT,
# PATCH, DELETE e GET depois do delete), /transfers/{id}/passageiros, o vinculo com
# passageiros e com a OS, e os erros 400 e 404.
# como rodar: bash scripts/api/test-transfers.sh (api de pe; ver common.sh).
# por que existe: o transfer e o centro da operacao (vira rota e faturamento).
source "$(dirname "$0")/common.sh"
echo "=== Transfers ==="

DIA="2030-02-11"

criar "/passageiros" "{\"nome\":\"Bia $SUFIXO\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"BB$SUFIXO\"}"
BIA=$NOVO_ID
criar "/passageiros" "{\"nome\":\"Ana $SUFIXO\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"AA$SUFIXO\"}"
ANA=$NOVO_ID
# as OS sao criadas antes do transfer pra limpeza apagar o transfer primeiro
criar "/ordens-servico" "{\"dataServico\":\"$DIA\"}"
OS=$NOVO_ID
criar "/ordens-servico" "{\"dataServico\":\"2030-02-12\"}"
OS_OUTRO_DIA=$NOVO_ID

testar "POST /transfers (com passageiros)" 201 POST "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"14:30:00\",\"origem\":\"GRU $SUFIXO\",\"destino\":\"Hotel\",\"valorBase\":300.00,\"moedaOrigem\":\"BRL\",\"passageiroIds\":[$BIA,$ANA]}"
ID=$(id_de "$BODY")
apagar_no_fim "/transfers/$ID"
check_valor "sem OS o transfer nasce AGUARDANDO_OS" "AGUARDANDO_OS" "$(valor_de "$BODY" status)"

testar "GET /transfers" 200 GET "/transfers"
testar "GET /transfers/$ID" 200 GET "/transfers/$ID"
testar "GET /transfers/$ID/passageiros" 200 GET "/transfers/$ID/passageiros"
check_valor "passageiros do transfer vem por nome (Ana primeiro)" "Ana $SUFIXO" "$(valor_de "$BODY" nome)"
testar "GET /transfers/buscar?status=AGUARDANDO_OS" 200 GET "/transfers/buscar?status=AGUARDANDO_OS"

testar "PUT /transfers/$ID" 200 PUT "/transfers/$ID" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU $SUFIXO\",\"destino\":\"Hotel Fasano\",\"valorBase\":350.00,\"moedaOrigem\":\"BRL\"}"
check_valor "PUT troca o destino" "Hotel Fasano" "$(valor_de "$BODY" destino)"
testar "PATCH /transfers/$ID (lista vazia limpa os passageiros)" 200 PATCH "/transfers/$ID" '{"passageiroIds":[]}'
testar "GET /transfers/$ID/passageiros (depois de limpar)" 200 GET "/transfers/$ID/passageiros"
check_valor "lista de passageiros vazia" "[]" "$BODY"

# vinculo com a OS
testar "PATCH /transfers/$ID (entra na OS do mesmo dia)" 200 PATCH "/transfers/$ID" "{\"osId\":$OS}"
check_valor "com OS o transfer fica CONFIRMADO" "CONFIRMADO" "$(valor_de "$BODY" status)"
testar "GET /transfers?osId= (so os da OS)" 200 GET "/transfers?osId=$OS"
check_valor "a OS tem 1 transfer" "1" "$(valor_de "$BODY" totalElementos)"
testar "PATCH /transfers/$ID (OS de outro dia) -> 400" 400 PATCH "/transfers/$ID" "{\"osId\":$OS_OUTRO_DIA}"
testar "PUT /transfers/$ID (sem osId sai da OS)" 200 PUT "/transfers/$ID" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"status\":\"CONFIRMADO\"}"
check_valor "fora da OS volta pra AGUARDANDO_OS" "AGUARDANDO_OS" "$(valor_de "$BODY" status)"

# caminhos tristes
testar "POST /transfers (sem data) -> 400" 400 POST "/transfers" '{"horaTransfer":"10:00:00","origem":"A","destino":"B"}'
testar "POST /transfers (status fora do enum) -> 400" 400 POST "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\",\"status\":\"XPTO\"}"
testar "POST /transfers (valor negativo) -> 400" 400 POST "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\",\"valorBase\":-1}"
testar "POST /transfers (moeda com 2 letras) -> 400" 400 POST "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\",\"moedaOrigem\":\"US\"}"
testar "POST /transfers (passageiro inexistente) -> 404" 404 POST "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\",\"passageiroIds\":[999999999]}"
testar "POST /transfers (OS inexistente) -> 404" 404 POST "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\",\"osId\":999999999}"
testar "GET /transfers/buscar?status=PERDIDO -> 400" 400 GET "/transfers/buscar?status=PERDIDO"
testar "GET /transfers/999999999 -> 404" 404 GET "/transfers/999999999"
testar "GET /transfers/999999999/passageiros -> 404" 404 GET "/transfers/999999999/passageiros"
testar "PUT /transfers/999999999 -> 404" 404 PUT "/transfers/999999999" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\"}"
testar "PATCH /transfers/999999999 -> 404" 404 PATCH "/transfers/999999999" '{"origem":"C"}'
testar "DELETE /transfers/999999999 -> 404" 404 DELETE "/transfers/999999999"

testar "DELETE /transfers/$ID" 204 DELETE "/transfers/$ID"
testar "GET /transfers/$ID (depois do delete) -> 404" 404 GET "/transfers/$ID"

resumo
