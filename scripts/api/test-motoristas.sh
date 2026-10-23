#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Motoristas ==="

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/motoristas" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"nome":"Joao Silva","cnh":"12345678900","telefone":"11999999999","latitudeAtual":-23.55,"longitudeAtual":-46.63}')
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /motoristas" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/motoristas")
check_status "GET /motoristas" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/motoristas/$ID")
check_status "GET /motoristas/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/motoristas/buscar?cnh=12345678900")
check_status "GET /motoristas/buscar?cnh=" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/motoristas/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"nome":"Joao Silva Jr","cnh":"12345678900","telefone":"11988888888","latitudeAtual":-23.5,"longitudeAtual":-46.6}')
check_status "PUT /motoristas/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/motoristas/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"telefone":"11977777777"}')
check_status "PATCH /motoristas/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/motoristas/$ID")
check_status "DELETE /motoristas/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/motoristas/$ID")
check_status "GET /motoristas/$ID (apos delete)" 404 "$STATUS"
echo ""