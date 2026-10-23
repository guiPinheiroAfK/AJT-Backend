#!/usr/bin/env bash
# o que testa: /api/cotacao. 200 com moedas validas (a api de cambio externa precisa
# responder), mesma moeda sem chamar a api externa, e 400 com codigo invalido ou faltando.
# como rodar: bash scripts/api/test-cotacao.sh (api de pe e com internet; ver common.sh).
# por que existe: a conversao do valor do transfer depende dessa rota.
source "$(dirname "$0")/common.sh"
echo "=== Cotacao ==="

testar "GET /cotacao?de=USD&para=BRL" 200 GET "/cotacao?de=USD&para=BRL"
if [ "$STATUS" == "502" ]; then
  echo "       (502 = a api de cambio externa nao respondeu; confira a internet da maquina da api)"
fi
check_valor "moeda de origem normalizada" "USD" "$(valor_de "$BODY" moedaOrigem)"
testar "GET /cotacao?de=eur&para=brl (minusculo)" 200 GET "/cotacao?de=eur&para=brl"
testar "GET /cotacao?de=BRL&para=BRL (mesma moeda)" 200 GET "/cotacao?de=BRL&para=BRL"
check_valor "mesma moeda tem taxa 1" "1" "$(valor_de "$BODY" taxa)"

testar "GET /cotacao?de=US&para=BRL -> 400" 400 GET "/cotacao?de=US&para=BRL"
testar "GET /cotacao?de=USD&para=BR1 -> 400" 400 GET "/cotacao?de=USD&para=BR1"
testar "GET /cotacao?de=USD (sem para) -> 400" 400 GET "/cotacao?de=USD"
testar "POST /cotacao -> 405" 405 POST "/cotacao" "{}"
testar "GET /cotacao (sem token) -> 401" 401 GET "/cotacao?de=USD&para=BRL" "" -

resumo
