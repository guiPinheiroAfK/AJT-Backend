#!/bin/bash
source "$(dirname "$0")/common.sh"
echo "=== Auth ==="

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" -d '{"username":"admin","senha":"admin123"}')
check_status "POST /auth/login (credenciais validas)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" -d '{"username":"usuario.invalido","senha":"errada"}')
check_status "POST /auth/login (credenciais invalidas)" 401 "$STATUS"
echo ""