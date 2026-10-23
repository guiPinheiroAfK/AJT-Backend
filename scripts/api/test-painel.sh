#!/usr/bin/env bash
# o que testa: /api/painel/resumo. os totais do dia sobem exatamente com os transfers que o
# script cria (contagem por status, cancelado fora do faturamento) e o faturamento so
# aparece pra ADMIN e GERENTE (null pra ATENDENTE e MOTORISTA).
# como rodar: bash scripts/api/test-painel.sh (api de pe; ver common.sh).
# por que existe: o faturamento e dado gerencial; e o numero que a gerencia olha todo dia.
# o script compara antes e depois, entao nao depende do que ja existe no banco.
source "$(dirname "$0")/common.sh"
echo "=== Painel ==="

DIA="2030-09-17"

# parte inteira de um numero json ("150.00" -> 150, "null" -> 0)
inteiro() {
  local v="${1%%.*}"
  [ "$v" == "null" ] || [ -z "$v" ] && v=0
  echo "$v"
}

testar "GET /painel/resumo?data= (antes)" 200 GET "/painel/resumo?data=$DIA"
TOTAL_ANTES=$(valor_de "$BODY" totalTransfers)
FAT_ANTES=$(inteiro "$(valor_de "$BODY" faturamentoDia)")

for extra in ',"valorBase":100.00' ',"valorBase":50.00' ',"valorBase":999.00,"status":"CANCELADO"'; do
  criar "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"09:00:00\",\"origem\":\"GRU $SUFIXO\",\"destino\":\"Hotel\"$extra}"
done

testar "GET /painel/resumo?data= (admin, depois)" 200 GET "/painel/resumo?data=$DIA"
check_valor "total de transfers do dia sobe 3" "$((TOTAL_ANTES + 3))" "$(valor_de "$BODY" totalTransfers)"
check_valor "faturamento do dia sobe 150 (cancelado fica fora)" "$((FAT_ANTES + 150))" "$(inteiro "$(valor_de "$BODY" faturamentoDia)")"
check_valor "data do resumo e a pedida" "$DIA" "$(valor_de "$BODY" data)"

criar_usuario GERENTE
testar "GET /painel/resumo (gerente ve faturamento)" 200 GET "/painel/resumo?data=$DIA" "" "$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")"
check_valor "gerente ve o mesmo faturamento" "$((FAT_ANTES + 150))" "$(inteiro "$(valor_de "$BODY" faturamentoDia)")"

for perfil in ATENDENTE MOTORISTA; do
  criar_usuario "$perfil"
  testar "GET /painel/resumo ($perfil)" 200 GET "/painel/resumo?data=$DIA" "" "$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")"
  check_valor "$perfil ve os totais" "$((TOTAL_ANTES + 3))" "$(valor_de "$BODY" totalTransfers)"
  check_valor "$perfil nao ve faturamento do dia" "null" "$(valor_de "$BODY" faturamentoDia)"
  check_valor "$perfil nao ve faturamento do mes" "null" "$(valor_de "$BODY" faturamentoMes)"
done

testar "GET /painel/resumo (sem data = hoje)" 200 GET "/painel/resumo"
testar "GET /painel/resumo?data=17-09-2030 -> 400" 400 GET "/painel/resumo?data=17-09-2030"
testar "PUT /painel/resumo -> 405" 405 PUT "/painel/resumo"
testar "GET /painel/resumo (sem token) -> 401" 401 GET "/painel/resumo" "" -

resumo
