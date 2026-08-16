
#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Passageiros ==="

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/passageiros" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"nome":"Maria Souza","tipoDocumento":"PASSAPORTE","documento":"AB123456","nacionalidade":"Brasileira"}')
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /passageiros" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/passageiros")
check_status "GET /passageiros" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/passageiros/$ID")
check_status "GET /passageiros/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/passageiros/buscar?nacionalidade=Brasileira")
check_status "GET /passageiros/buscar?nacionalidade=" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/passageiros/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"nome":"Maria Souza Lima","tipoDocumento":"PASSAPORTE","documento":"AB123456","nacionalidade":"Brasileira"}')
check_status "PUT /passageiros/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/passageiros/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"nacionalidade":"Portuguesa"}')
check_status "PATCH /passageiros/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/passageiros/$ID")
check_status "DELETE /passageiros/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/passageiros/$ID")
check_status "GET /passageiros/$ID (apos delete)" 404 "$STATUS"
echo ""