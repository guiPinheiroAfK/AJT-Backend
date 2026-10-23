#!/usr/bin/env bash
# o que testa: /api/auth: login certo e errado, /me com e sem token, troca de senha
# invalidando o token antigo e o bloqueio por tentativas (429).
# como rodar: bash scripts/api/test-auth.sh (api de pe; ver common.sh).
# por que existe: tudo depende do login. o bloqueio e a troca usam usuario descartavel,
# nunca o admin, pra nao trancar quem roda o script.
source "$(dirname "$0")/common.sh"
echo "=== Auth ==="

testar "POST /auth/login (admin, credenciais certas)" 200 POST "/auth/login" "{\"username\":\"$AJT_USER\",\"senha\":\"$AJT_PASS\"}" -
check_valor "login devolve tipo Bearer" "Bearer" "$(valor_de "$BODY" tipo)"
testar "POST /auth/login (senha errada)" 401 POST "/auth/login" '{"username":"usuario.que.nao.existe","senha":"errada123"}' -
testar "POST /auth/login (corpo sem campos)" 400 POST "/auth/login" '{}' -

testar "GET /auth/me (com token)" 200 GET "/auth/me"
check_valor "/me devolve o usuario logado" "$AJT_USER" "$(valor_de "$BODY" username)"
testar "GET /auth/me (sem token)" 401 GET "/auth/me" "" -
testar "GET /auth/me (token invalido)" 401 GET "/auth/me" "" "token.invalido.xyz"

# troca de senha com usuario descartavel
criar_usuario ATENDENTE
TOKEN_ANTIGO=$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")
# o iat do token tem precisao de segundos: espera pra troca cair num segundo depois
sleep 1
testar "PUT /auth/senha (senha atual errada)" 400 PUT "/auth/senha" '{"senhaAtual":"nao-e-essa-1","novaSenha":"senha-nova-123"}' "$TOKEN_ANTIGO"
testar "PUT /auth/senha (nova senha curta)" 400 PUT "/auth/senha" "{\"senhaAtual\":\"$NOVO_SENHA\",\"novaSenha\":\"curta\"}" "$TOKEN_ANTIGO"
testar "PUT /auth/senha (troca valida)" 200 PUT "/auth/senha" "{\"senhaAtual\":\"$NOVO_SENHA\",\"novaSenha\":\"senha-nova-123\"}" "$TOKEN_ANTIGO"
TOKEN_NOVO=$(valor_de "$BODY" token)
testar "GET /auth/me (token antigo depois da troca)" 401 GET "/auth/me" "" "$TOKEN_ANTIGO"
testar "GET /auth/me (token novo)" 200 GET "/auth/me" "" "$TOKEN_NOVO"
testar "POST /auth/login (senha antiga depois da troca)" 401 POST "/auth/login" "{\"username\":\"$NOVO_USUARIO\",\"senha\":\"$NOVO_SENHA\"}" -

# bloqueio por tentativas, outro usuario descartavel (o limite vem de AJT_LOGIN_MAX_TENTATIVAS)
criar_usuario ATENDENTE
ULTIMO=""
for i in 1 2 3 4 5 6 7 8 9 10; do
  req POST "/auth/login" "{\"username\":\"$NOVO_USUARIO\",\"senha\":\"errada-$i\"}" -
  ULTIMO="$STATUS"
  [ "$STATUS" == "429" ] && break
done
check_status "POST /auth/login (senha errada repetida ate bloquear)" 429 "$ULTIMO"
testar "POST /auth/login (senha certa durante o bloqueio)" 429 POST "/auth/login" "{\"username\":\"$NOVO_USUARIO\",\"senha\":\"$NOVO_SENHA\"}" -

resumo
