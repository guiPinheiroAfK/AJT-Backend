#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Veiculos ==="

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/veiculos" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"label":"Van 01","placa":"ABC1D23","capacidade":15,"tipo":"VAN","marca":"Mercedes"}')
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /veiculos" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/veiculos")
check_status "GET /veiculos" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/veiculos/$ID")
check_status "GET /veiculos/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/veiculos/buscar?placa=ABC1D23")
check_status "GET /veiculos/buscar?placa=" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/veiculos/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"label":"Van 01 Renovada","placa":"ABC1D23","capacidade":16,"tipo":"VAN","marca":"Mercedes"}')
check_status "PUT /veiculos/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/veiculos/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"capacidade":18}')
check_status "PATCH /veiculos/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/veiculos/$ID")
check_status "DELETE /veiculos/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/veiculos/$ID")
check_status "GET /veiculos/$ID (apos delete)" 404 "$STATUS"
echo ""