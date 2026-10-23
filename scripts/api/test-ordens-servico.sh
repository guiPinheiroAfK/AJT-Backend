#!/usr/bin/env bash
# o que testa: CRUD de /api/ordens-servico (POST, GET lista, GET id, GET buscar por status,
# PUT, PATCH, DELETE e GET depois do delete), o PDF (200 + application/pdf), /minhas e os
# erros 400, 403, 404 e 409.
# como rodar: bash scripts/api/test-ordens-servico.sh (api de pe; ver common.sh).
# por que existe: a OS agrupa o dia do motorista; o PDF e o papel que vai com ele.
source "$(dirname "$0")/common.sh"
echo "=== Ordens de servico ==="

DIA="2030-05-13"
criar "/motoristas" "{\"nome\":\"Carlos $SUFIXO\",\"cnh\":\"O${SUFIXO: -12}\"}"
MOTORISTA=$NOVO_ID
criar "/veiculos" "{\"label\":\"Van OS\",\"placa\":\"O${SUFIXO: -9}\",\"capacidade\":15}"
VEICULO=$NOVO_ID

testar "POST /ordens-servico" 201 POST "/ordens-servico" "{\"dataServico\":\"$DIA\",\"motoristaId\":$MOTORISTA,\"veiculoId\":$VEICULO}"
ID=$(id_de "$BODY")
apagar_no_fim "/ordens-servico/$ID"
check_valor "OS nasce ABERTA" "ABERTA" "$(valor_de "$BODY" status)"

testar "GET /ordens-servico" 200 GET "/ordens-servico"
testar "GET /ordens-servico?motoristaId= (filtro)" 200 GET "/ordens-servico?motoristaId=$MOTORISTA"
check_valor "o motorista tem 1 OS" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /ordens-servico/$ID" 200 GET "/ordens-servico/$ID"
testar "GET /ordens-servico/buscar?status=ABERTA" 200 GET "/ordens-servico/buscar?status=ABERTA"

# pdf: so o status e o content-type (o corpo e binario)
PDF=$(curl -s -o /dev/null -w "%{http_code} %{content_type}" -H "Authorization: Bearer $TOKEN" "$BASE_URL/ordens-servico/$ID/pdf")
check_status "GET /ordens-servico/$ID/pdf (status)" 200 "${PDF%% *}"
check_valor "GET /ordens-servico/$ID/pdf (content-type)" "application/pdf" "${PDF#* }"

# OS com transfer nao muda de dia e nao e excluida (antes de finalizar: OS finalizada nao recebe transfer)
criar "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"10:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"osId\":$ID}"
TRANSFER=$NOVO_ID
testar "PATCH /ordens-servico/$ID (mudar dia com transfer) -> 400" 400 PATCH "/ordens-servico/$ID" '{"dataServico":"2030-05-14"}'
testar "DELETE /ordens-servico/$ID (com transfer) -> 409" 409 DELETE "/ordens-servico/$ID"
req DELETE "/transfers/$TRANSFER"

testar "PUT /ordens-servico/$ID" 200 PUT "/ordens-servico/$ID" "{\"dataServico\":\"$DIA\",\"motoristaId\":$MOTORISTA,\"veiculoId\":$VEICULO,\"status\":\"EM_ANDAMENTO\"}"
check_valor "PUT troca o status" "EM_ANDAMENTO" "$(valor_de "$BODY" status)"
testar "PATCH /ordens-servico/$ID" 200 PATCH "/ordens-servico/$ID" '{"status":"FINALIZADA"}'

# /minhas e so do perfil MOTORISTA
testar "GET /ordens-servico/minhas (admin) -> 403" 403 GET "/ordens-servico/minhas"
criar_usuario MOTORISTA
TOKEN_MOTORISTA=$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")
testar "GET /ordens-servico/minhas (motorista sem cadastro ligado)" 200 GET "/ordens-servico/minhas?data=$DIA" "" "$TOKEN_MOTORISTA"
check_valor "motorista sem vinculo nao ve nenhuma OS" "[]" "$BODY"
testar "GET /ordens-servico/minhas?data=hoje -> 400" 400 GET "/ordens-servico/minhas?data=hoje" "" "$TOKEN_MOTORISTA"

# caminhos tristes
testar "POST /ordens-servico (sem data) -> 400" 400 POST "/ordens-servico" '{}'
testar "POST /ordens-servico (data fora do formato) -> 400" 400 POST "/ordens-servico" '{"dataServico":"21/10/2026"}'
testar "POST /ordens-servico (status fora do enum) -> 400" 400 POST "/ordens-servico" "{\"dataServico\":\"$DIA\",\"status\":\"PAUSADA\"}"
testar "POST /ordens-servico (motorista inexistente) -> 404" 404 POST "/ordens-servico" "{\"dataServico\":\"$DIA\",\"motoristaId\":999999999}"
testar "GET /ordens-servico/buscar (sem status) -> 400" 400 GET "/ordens-servico/buscar"
testar "GET /ordens-servico/999999999 -> 404" 404 GET "/ordens-servico/999999999"
testar "GET /ordens-servico/999999999/pdf -> 404" 404 GET "/ordens-servico/999999999/pdf"
testar "PUT /ordens-servico/999999999 -> 404" 404 PUT "/ordens-servico/999999999" "{\"dataServico\":\"$DIA\"}"
testar "PATCH /ordens-servico/999999999 -> 404" 404 PATCH "/ordens-servico/999999999" '{"status":"CANCELADA"}'
testar "DELETE /ordens-servico/999999999 -> 404" 404 DELETE "/ordens-servico/999999999"

testar "DELETE /ordens-servico/$ID" 204 DELETE "/ordens-servico/$ID"
testar "GET /ordens-servico/$ID (depois do delete) -> 404" 404 GET "/ordens-servico/$ID"

resumo
