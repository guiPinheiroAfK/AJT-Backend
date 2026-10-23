#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Transfers ==="

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/transfers" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"dataTransfer":"2026-09-20","horaTransfer":"14:30:00","origem":"Aeroporto GRU","destino":"Hotel Copacabana","valorBase":250.00,"moedaOrigem":"BRL"}')
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /transfers" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/transfers")
check_status "GET /transfers" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/transfers/$ID")
check_status "GET /transfers/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/transfers/buscar?status=AGUARDANDO_OS")
check_status "GET /transfers/buscar?status=" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/transfers/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"dataTransfer":"2026-09-21","horaTransfer":"15:00:00","origem":"Aeroporto GRU","destino":"Hotel Ipanema","status":"CONFIRMADO","valorBase":300.00,"moedaOrigem":"BRL"}')
check_status "PUT /transfers/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/transfers/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"status":"EM_ANDAMENTO"}')
check_status "PATCH /transfers/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/transfers/$ID")
check_status "DELETE /transfers/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/transfers/$ID")
check_status "GET /transfers/$ID (apos delete)" 404 "$STATUS"
echo ""
