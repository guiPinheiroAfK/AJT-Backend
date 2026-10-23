#!/usr/bin/env bash
# o que testa: CRUD de /api/pontos-coleta (POST, GET lista, GET id, GET por transfer, PUT,
# PATCH, DELETE e GET depois do delete), a ordem dos pontos do transfer e os erros 400 e 404.
# como rodar: bash scripts/api/test-pontos-coleta.sh (api de pe; ver common.sh).
# por que existe: e a sequencia de coleta que o motorista segue na rua.
source "$(dirname "$0")/common.sh"
echo "=== Pontos de coleta ==="

criar "/transfers" "{\"dataTransfer\":\"2030-03-05\",\"horaTransfer\":\"14:30:00\",\"origem\":\"GRU $SUFIXO\",\"destino\":\"Hotel\"}"
TRANSFER=$NOVO_ID

testar "POST /pontos-coleta (ordem 2)" 201 POST "/pontos-coleta" "{\"transferId\":$TRANSFER,\"localColeta\":\"Portao 5\",\"ordemParada\":2,\"horarioPrevisto\":\"14:10:00\",\"latitude\":-23.43,\"longitude\":-46.47}"
ID=$(id_de "$BODY")
apagar_no_fim "/pontos-coleta/$ID"
criar "/pontos-coleta" "{\"transferId\":$TRANSFER,\"localColeta\":\"Portao 3\",\"ordemParada\":1,\"latitude\":-23.43,\"longitude\":-46.47}"

testar "GET /pontos-coleta" 200 GET "/pontos-coleta"
testar "GET /pontos-coleta/$ID" 200 GET "/pontos-coleta/$ID"
testar "GET /pontos-coleta/transfer/$TRANSFER" 200 GET "/pontos-coleta/transfer/$TRANSFER"
check_valor "pontos do transfer vem na ordem de parada (Portao 3 primeiro)" "Portao 3" "$(valor_de "$BODY" localColeta)"

testar "PUT /pontos-coleta/$ID" 200 PUT "/pontos-coleta/$ID" "{\"transferId\":$TRANSFER,\"localColeta\":\"Portao 7\",\"ordemParada\":2,\"horarioPrevisto\":\"14:20:00\",\"latitude\":-23.43,\"longitude\":-46.47}"
check_valor "PUT troca o local" "Portao 7" "$(valor_de "$BODY" localColeta)"
testar "PATCH /pontos-coleta/$ID" 200 PATCH "/pontos-coleta/$ID" '{"horarioPrevisto":"14:30:00"}'

# caminhos tristes
testar "POST /pontos-coleta (sem transfer) -> 400" 400 POST "/pontos-coleta" '{"localColeta":"P","latitude":0,"longitude":0}'
testar "POST /pontos-coleta (latitude fora da faixa) -> 400" 400 POST "/pontos-coleta" "{\"transferId\":$TRANSFER,\"localColeta\":\"P\",\"latitude\":91,\"longitude\":0}"
testar "POST /pontos-coleta (horario invalido) -> 400" 400 POST "/pontos-coleta" "{\"transferId\":$TRANSFER,\"localColeta\":\"P\",\"latitude\":0,\"longitude\":0,\"horarioPrevisto\":\"25:99\"}"
testar "POST /pontos-coleta (transfer inexistente) -> 404" 404 POST "/pontos-coleta" '{"transferId":999999999,"localColeta":"P","latitude":0,"longitude":0}'
testar "GET /pontos-coleta/transfer/xyz -> 400" 400 GET "/pontos-coleta/transfer/xyz"
testar "GET /pontos-coleta/999999999 -> 404" 404 GET "/pontos-coleta/999999999"
testar "PUT /pontos-coleta/999999999 -> 404" 404 PUT "/pontos-coleta/999999999" "{\"transferId\":$TRANSFER,\"localColeta\":\"P\",\"latitude\":0,\"longitude\":0}"
testar "PATCH /pontos-coleta/999999999 -> 404" 404 PATCH "/pontos-coleta/999999999" '{"localColeta":"Outro"}'
testar "DELETE /pontos-coleta/999999999 -> 404" 404 DELETE "/pontos-coleta/999999999"

testar "DELETE /pontos-coleta/$ID" 204 DELETE "/pontos-coleta/$ID"
testar "GET /pontos-coleta/$ID (depois do delete) -> 404" 404 GET "/pontos-coleta/$ID"

resumo
