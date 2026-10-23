#!/usr/bin/env bash
# o que testa: /api/auditoria. leitura 200 pra ADMIN e GERENTE e 403 pros outros perfis,
# filtro por tabela e pelo historico de um registro (tabela + registroId), e a mensagem de
# mudanca de status gravada numa edicao.
# como rodar: bash scripts/api/test-auditoria.sh (api de pe; ver common.sh).
# por que existe: e o que responde "quem mexeu nisso e quando".
source "$(dirname "$0")/common.sh"
echo "=== Auditoria ==="

criar "/transfers" "{\"dataTransfer\":\"2030-10-01\",\"horaTransfer\":\"10:00:00\",\"origem\":\"GRU $SUFIXO\",\"destino\":\"Hotel\"}"
TRANSFER=$NOVO_ID
req PATCH "/transfers/$TRANSFER" '{"status":"CONFIRMADO"}'

testar "GET /auditoria (admin)" 200 GET "/auditoria"
testar "GET /auditoria?tabela=transfers" 200 GET "/auditoria?tabela=transfers&size=5"
testar "GET /auditoria?tabela=transfers&registroId=" 200 GET "/auditoria?tabela=transfers&registroId=$TRANSFER"
check_valor "historico do transfer tem criacao e mudanca de status" "2" "$(valor_de "$BODY" totalElementos)"
echo "$BODY" | grep -q "AGUARDANDO_OS -> CONFIRMADO"
check_status "historico mostra a mudanca de status" "0" "$?"
testar "GET /auditoria?tabela=motoristas&registroId= (outra tabela, mesmo id)" 200 GET "/auditoria?tabela=motoristas&registroId=$TRANSFER"
echo "$BODY" | grep -q "GRU $SUFIXO"
check_status "outra tabela nao mistura o historico do transfer" "1" "$?"
testar "GET /auditoria?registroId=sete -> 400" 400 GET "/auditoria?tabela=transfers&registroId=sete"
testar "POST /auditoria (so leitura) -> 405" 405 POST "/auditoria" "{}"

criar_usuario GERENTE
testar "GET /auditoria (gerente)" 200 GET "/auditoria" "" "$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")"
for perfil in ATENDENTE MOTORISTA; do
  criar_usuario "$perfil"
  testar "GET /auditoria ($perfil) -> 403" 403 GET "/auditoria" "" "$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")"
done
testar "GET /auditoria (sem token) -> 401" 401 GET "/auditoria" "" -

resumo
