#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Ordens de Servico ==="

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/ordens-servico" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"dataServico":"2026-09-20"}')
STATUS=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check_status "POST /ordens-servico" 201 "$STATUS"
ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/ordens-servico")
check_status "GET /ordens-servico" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/ordens-servico/$ID")
check_status "GET /ordens-servico/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/ordens-servico/buscar?status=ABERTA")
check_status "GET /ordens-servico/buscar?status=" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/ordens-servico/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" \
  -d '{"dataServico":"2026-09-21","status":"EM_ANDAMENTO"}')
check_status "PUT /ordens-servico/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/ordens-servico/$ID" \
  -H "Content-Type: application/json" -H "$AUTH_HEADER" -d '{"status":"FINALIZADA"}')
check_status "PATCH /ordens-servico/$ID" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_HEADER" "$BASE_URL/ordens-servico/$ID")
check_status "DELETE /ordens-servico/$ID" 204 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_HEADER" "$BASE_URL/ordens-servico/$ID")
check_status "GET /ordens-servico/$ID (apos delete)" 404 "$STATUS"
echo ""
