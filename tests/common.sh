#!/bin/bash
BASE_URL="http://localhost:8080/api"

pass() { echo -e "\033[32m[OK]\033[0m $1 -> status $2"; }
fail() { echo -e "\033[31m[FAIL]\033[0m $1 -> esperado $2, recebido $3"; }

check_status() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$actual" == "$expected" ]; then
    pass "$desc" "$actual"
  else
    fail "$desc" "$expected" "$actual"
  fi
}

TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","senha":"admin123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo -e "\033[31m[ERRO]\033[0m Nao consegui logar como admin. Verifique se V2__seed_admin.sql rodou (docker compose down -v && sobe de novo)."
  exit 1
fi

AUTH_HEADER="Authorization: Bearer $TOKEN"