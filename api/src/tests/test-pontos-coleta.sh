#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Pontos de Coleta ==="

TRANSFER_RESP=$(curl -s -X POST "$BASE_URL/transfers" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"dataTransfer":"2026-09-20","horaTransfer":"14:30:00","origem":"Aeroporto GRU","destino":"Hotel Copacabana"}')
TRANSFER_ID=$(echo "$TRANSFER_RESP" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/pontos-coleta" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d "{\"transferId\":$TRANSFER_ID,\"localColeta\":\"Portao 3\",\"ordemParada\":1,\"horarioPrevisto\":\"14:00:00\",\"latitude\":-23.4356,\"longitude\":-46.4731}")
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /pontos-coleta" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/pontos-coleta")
check_status "GET /pontos-coleta" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/pontos-coleta/$ID")
check_status "GET /pontos-coleta/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/pontos-coleta/transfer/$TRANSFER_ID")
check_status "GET /pontos-coleta/transfer/$TRANSFER_ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/pontos-coleta/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d "{\"transferId\":$TRANSFER_ID,\"localColeta\":\"Portao 5\",\"ordemParada\":2,\"horarioPrevisto\":\"14:15:00\",\"latitude\":-23.5,\"longitude\":-46.5}")
check_status "PUT /pontos-coleta/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/pontos-coleta/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"localColeta":"Portao 7"}')
check_status "PATCH /pontos-coleta/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/pontos-coleta/$ID")
check_status "DELETE /pontos-coleta/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/pontos-coleta/$ID")
check_status "GET /pontos-coleta/$ID (apos delete)" 404 "$STATUS"

curl -s -o /dev/null -X DELETE -H "$AUTH_HEADER" "$BASE_URL/transfers/$TRANSFER_ID"
echo ""
