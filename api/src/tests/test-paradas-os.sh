#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Paradas OS ==="

OS_RESP=$(curl -s -X POST "$BASE_URL/ordens-servico" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"dataServico":"2026-09-20"}')
OS_ID=$(echo "$OS_RESP" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

TRANSFER_RESP=$(curl -s -X POST "$BASE_URL/transfers" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"dataTransfer":"2026-09-20","horaTransfer":"14:30:00","origem":"Aeroporto GRU","destino":"Hotel Copacabana"}')
TRANSFER_ID=$(echo "$TRANSFER_RESP" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/paradas-os" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d "{\"osId\":$OS_ID,\"ordemParada\":1,\"localParada\":\"Portao 3\",\"latitude\":-23.4356,\"longitude\":-46.4731,\"horarioPrevisto\":\"14:00:00\",\"acao\":\"EMBARQUE\",\"transferIds\":[$TRANSFER_ID]}")
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /paradas-os" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/paradas-os")
check_status "GET /paradas-os" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/paradas-os/$ID")
check_status "GET /paradas-os/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/paradas-os/ordem-servico/$OS_ID")
check_status "GET /paradas-os/ordem-servico/$OS_ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/paradas-os/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d "{\"osId\":$OS_ID,\"ordemParada\":1,\"localParada\":\"Portao 7\",\"latitude\":-23.4,\"longitude\":-46.4,\"acao\":\"DESEMBARQUE\",\"statusParada\":\"CONCLUIDA\",\"transferIds\":[$TRANSFER_ID]}")
check_status "PUT /paradas-os/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/paradas-os/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"statusParada":"EM_ANDAMENTO"}')
check_status "PATCH /paradas-os/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/paradas-os/$ID")
check_status "DELETE /paradas-os/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/paradas-os/$ID")
check_status "GET /paradas-os/$ID (apos delete)" 404 "$STATUS"

curl -s -o /dev/null -X DELETE -H "$AUTH_HEADER" "$BASE_URL/transfers/$TRANSFER_ID"
curl -s -o /dev/null -X DELETE -H "$AUTH_HEADER" "$BASE_URL/ordens-servico/$OS_ID"
echo ""
