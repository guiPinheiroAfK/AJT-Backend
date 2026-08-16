#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Usuarios ==="

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/usuarios" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"nome":"Usuario Teste","username":"usuario.teste","senha":"senha123","role":"GERENTE"}')
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /usuarios" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/usuarios")
check_status "GET /usuarios" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/usuarios/$ID")
check_status "GET /usuarios/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/usuarios/buscar?username=usuario.teste")
check_status "GET /usuarios/buscar?username=" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/usuarios/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"nome":"Usuario Teste 2","username":"usuario.teste","senha":"senha123","role":"GERENTE"}')
check_status "PUT /usuarios/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/usuarios/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"role":"ATENDENTE"}')
check_status "PATCH /usuarios/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/usuarios/$ID")
check_status "DELETE /usuarios/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/usuarios/$ID")
check_status "GET /usuarios/$ID (apos delete)" 404 "$STATUS"
echo ""