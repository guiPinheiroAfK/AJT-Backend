#!/usr/bin/env bash
# base dos scripts de verificacao da api (scripts/api/test-*.sh). nao roda sozinho:
# cada test-*.sh faz "source" deste arquivo.
#
# como usar (com a api de pe):
#   bash scripts/api/test-motoristas.sh
#   AJT_URL=http://localhost:9090 AJT_USER=admin AJT_PASS=minhaSenha bash scripts/api/run-all.sh
#
# depende so de bash e curl (+ grep e sed). roda no git bash do windows.
# cada script cria os proprios dados com sufixo unico e apaga tudo no fim (trap EXIT),
# entao pode rodar duas vezes seguidas sem dar 400/409 de dado duplicado.

BASE_URL="${AJT_URL:-http://localhost:8080}/api"
AJT_USER="${AJT_USER:-admin}"
AJT_PASS="${AJT_PASS:-admin123}"

PASS=0
FAIL=0
STATUS=""
BODY=""
# valor unico desta execucao (placa, cnh, username, textos de busca)
SUFIXO="$(date +%s)$RANDOM"
# rotas pra apagar no fim, apagadas na ordem inversa da criacao
LIMPEZA=()

ok()    { echo -e "\033[32m[OK]\033[0m   $1 -> $2"; PASS=$((PASS + 1)); }
falha() { echo -e "\033[31m[FAIL]\033[0m $1 -> esperado $2, veio $3"; FAIL=$((FAIL + 1)); }

# compara o esperado com o recebido e conta OK/FAIL
check_status() {
  local desc="$1" esperado="$2" recebido="$3"
  if [ "$recebido" == "$esperado" ]; then
    ok "$desc" "$recebido"
  else
    falha "$desc" "$esperado" "$recebido"
    [ -n "$BODY" ] && echo "       corpo: $(echo "$BODY" | head -c 300)"
  fi
}

# mesmo que check_status, pra conferir um valor do corpo (le melhor no script)
check_valor() {
  check_status "$@"
}

# req METODO ROTA [CORPO] [TOKEN]
# faz a chamada e deixa o resultado em STATUS e BODY. sem TOKEN usa o do admin;
# TOKEN "-" manda sem Authorization.
req() {
  local metodo="$1" rota="$2" corpo="${3:-}" token="${4-$TOKEN}"
  local args=(-s -w "\n%{http_code}" -X "$metodo" "$BASE_URL$rota")
  if [ -n "$token" ] && [ "$token" != "-" ]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [ -n "$corpo" ]; then
    args+=(-H "Content-Type: application/json" -d "$corpo")
  fi
  local resp
  resp=$(curl "${args[@]}")
  STATUS="${resp##*$'\n'}"
  BODY="${resp%$'\n'*}"
  [ "$BODY" == "$STATUS" ] && BODY=""
}

# testar DESCRICAO STATUS_ESPERADO METODO ROTA [CORPO] [TOKEN]: req + check_status
testar() {
  local desc="$1" esperado="$2"
  shift 2
  req "$@"
  check_status "$desc" "$esperado" "$STATUS"
}

# valor_de CORPO CAMPO: primeiro valor do campo no json (texto ou numero, sem aspas)
valor_de() {
  echo "$1" | grep -o "\"$2\":\"\?[^\",}]*" | head -1 | sed "s/^\"$2\":\"\{0,1\}//"
}

id_de() {
  valor_de "$1" id
}

# login_como USUARIO SENHA: escreve o token (vazio se o login falhar)
login_como() {
  local resp
  resp=$(curl -s -X POST "$BASE_URL/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"senha\":\"$2\"}")
  valor_de "$resp" token
}

# apagar_no_fim ROTA: registra pra limpeza (ex: /motoristas/12)
apagar_no_fim() {
  LIMPEZA+=("$1")
}

# criar_usuario ROLE [MOTORISTA_ID]: cria pelo admin e deixa NOVO_USUARIO, NOVO_SENHA e NOVO_ID.
# usa variaveis (e nao echo) pra o registro da limpeza valer no script que chamou.
criar_usuario() {
  local role="$1" motorista="${2:-}"
  NOVO_USUARIO="ajt-$(echo "$role" | tr 'A-Z' 'a-z')-$SUFIXO-$RANDOM"
  NOVO_SENHA="senha-$SUFIXO"
  local extra=""
  [ -n "$motorista" ] && extra=",\"motoristaId\":$motorista"
  req POST "/usuarios" "{\"nome\":\"Teste $role\",\"username\":\"$NOVO_USUARIO\",\"senha\":\"$NOVO_SENHA\",\"role\":\"$role\"$extra}"
  NOVO_ID=$(id_de "$BODY")
  if [ "$STATUS" != "201" ] || [ -z "$NOVO_ID" ]; then
    echo -e "\033[31m[ERRO]\033[0m nao consegui criar usuario $role (status $STATUS): $(echo "$BODY" | head -c 200)"
    NOVO_ID=""
    return 1
  fi
  apagar_no_fim "/usuarios/$NOVO_ID"
}

# criar ROTA CORPO: cria pelo admin, registra pra limpeza e deixa o id em NOVO_ID
criar() {
  req POST "$1" "$2"
  NOVO_ID=$(id_de "$BODY")
  if [ "$STATUS" != "201" ] || [ -z "$NOVO_ID" ]; then
    echo -e "\033[31m[ERRO]\033[0m preparo falhou: POST $1 (status $STATUS): $(echo "$BODY" | head -c 200)"
    NOVO_ID=""
    return 1
  fi
  apagar_no_fim "$1/$NOVO_ID"
}

limpar() {
  local i
  for ((i = ${#LIMPEZA[@]} - 1; i >= 0; i--)); do
    curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $TOKEN" "$BASE_URL${LIMPEZA[$i]}"
  done
}
trap limpar EXIT

# imprime o total do script e sai com 1 se alguma verificacao falhou
resumo() {
  echo "RESUMO $(basename "$0"): OK=$PASS FAIL=$FAIL"
  echo ""
  [ "$FAIL" -eq 0 ] || exit 1
  exit 0
}

TOKEN=$(login_como "$AJT_USER" "$AJT_PASS")
if [ -z "$TOKEN" ]; then
  echo -e "\033[31m[ERRO]\033[0m nao consegui logar como $AJT_USER em $BASE_URL."
  echo "       confira se a api esta de pe (AJT_URL) e a senha (AJT_PASS). o admin padrao vem do"
  echo "       V5__Seed_Admin.sql; se a senha foi trocada, passe AJT_PASS=<senha>."
  exit 1
fi
