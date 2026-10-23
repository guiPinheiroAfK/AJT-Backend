#!/usr/bin/env bash
# o que testa: a HU07 de ponta a ponta. o admin liga um usuario MOTORISTA a um cadastro de
# motorista (V11); a OS do dia aparece em /minhas so pro motorista certo; a OS de outro
# motorista "nao existe" pra ele (404 na OS, no pdf e nas paradas); e o motorista so
# altera o status da parada (403 em qualquer outro campo).
# como rodar: bash scripts/api/test-motorista-minhas-os.sh (api de pe; ver common.sh).
# por que existe: motorista vendo a rota de outro e vazamento que ninguem percebe na tela.
source "$(dirname "$0")/common.sh"
echo "=== Motorista: minhas OS (HU07) ==="

DIA="2030-08-20"

criar "/motoristas" "{\"nome\":\"Eu $SUFIXO\",\"cnh\":\"E${SUFIXO: -12}\"}"
EU=$NOVO_ID
criar "/motoristas" "{\"nome\":\"Outro $SUFIXO\",\"cnh\":\"T${SUFIXO: -12}\"}"
OUTRO=$NOVO_ID
criar "/ordens-servico" "{\"dataServico\":\"$DIA\",\"motoristaId\":$EU}"
MINHA=$NOVO_ID
criar "/ordens-servico" "{\"dataServico\":\"$DIA\",\"motoristaId\":$OUTRO}"
DE_OUTRO=$NOVO_ID
criar "/paradas-os" "{\"osId\":$MINHA,\"ordemParada\":1,\"localParada\":\"Aeroporto\"}"
PARADA_MINHA=$NOVO_ID
criar "/paradas-os" "{\"osId\":$DE_OUTRO,\"ordemParada\":1,\"localParada\":\"Rodoviaria\"}"
PARADA_DE_OUTRO=$NOVO_ID

# vinculo usuario -> motorista (V11)
criar_usuario MOTORISTA "$EU"
check_status "admin cria usuario MOTORISTA ligado ao cadastro" 201 "$STATUS"
check_valor "usuario volta com o motoristaId" "$EU" "$(valor_de "$BODY" motoristaId)"
TOKEN_EU=$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")
testar "segundo usuario pro mesmo motorista -> 400" 400 POST "/usuarios" "{\"nome\":\"Dup\",\"username\":\"dup-$SUFIXO\",\"senha\":\"senha12345\",\"role\":\"MOTORISTA\",\"motoristaId\":$EU}"
testar "usuario ATENDENTE ligado a motorista -> 400" 400 POST "/usuarios" "{\"nome\":\"Ana\",\"username\":\"ana-$SUFIXO\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\",\"motoristaId\":$OUTRO}"
criar_usuario MOTORISTA "$OUTRO"
TOKEN_OUTRO=$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")

# /minhas
testar "GET /ordens-servico/minhas?data= (eu)" 200 GET "/ordens-servico/minhas?data=$DIA" "" "$TOKEN_EU"
check_valor "minhas OS trazem a minha" "$MINHA" "$(id_de "$BODY")"
echo "$BODY" | grep -q "\"id\":$DE_OUTRO,"
check_status "minhas OS nao trazem a do outro" "1" "$?"
check_valor "minhas OS trazem a parada" "Aeroporto" "$(valor_de "$BODY" localParada)"
testar "GET /ordens-servico/minhas?data= (outro)" 200 GET "/ordens-servico/minhas?data=$DIA" "" "$TOKEN_OUTRO"
check_valor "o outro ve so a dele" "$DE_OUTRO" "$(id_de "$BODY")"
testar "GET /ordens-servico/minhas (outro dia, vazio)" 200 GET "/ordens-servico/minhas?data=2030-08-21" "" "$TOKEN_EU"
check_valor "outro dia nao tem OS" "[]" "$BODY"

# OS do outro motorista nao existe pra mim
testar "GET /ordens-servico/$MINHA (minha)" 200 GET "/ordens-servico/$MINHA" "" "$TOKEN_EU"
testar "GET /ordens-servico/$DE_OUTRO (do outro) -> 404" 404 GET "/ordens-servico/$DE_OUTRO" "" "$TOKEN_EU"
testar "GET /ordens-servico/$DE_OUTRO/pdf (do outro) -> 404" 404 GET "/ordens-servico/$DE_OUTRO/pdf" "" "$TOKEN_EU"
testar "GET /paradas-os/ordem-servico/$DE_OUTRO (do outro) -> 404" 404 GET "/paradas-os/ordem-servico/$DE_OUTRO" "" "$TOKEN_EU"
testar "GET /ordens-servico?motoristaId= do outro (filtro ignorado pro motorista)" 200 GET "/ordens-servico?motoristaId=$OUTRO&size=100" "" "$TOKEN_EU"
echo "$BODY" | grep -q "\"id\":$DE_OUTRO,"
check_status "listagem do motorista nao mostra a OS do outro" "1" "$?"

# motorista so altera o status da parada
testar "PATCH /paradas-os/$PARADA_MINHA (status)" 200 PATCH "/paradas-os/$PARADA_MINHA" '{"statusParada":"CONCLUIDA"}' "$TOKEN_EU"
check_valor "status da parada mudou" "CONCLUIDA" "$(valor_de "$BODY" statusParada)"
testar "PATCH /paradas-os/$PARADA_MINHA (local) -> 403" 403 PATCH "/paradas-os/$PARADA_MINHA" '{"localParada":"Outro lugar"}' "$TOKEN_EU"
testar "PATCH /paradas-os/$PARADA_DE_OUTRO (parada do outro) -> 404" 404 PATCH "/paradas-os/$PARADA_DE_OUTRO" '{"statusParada":"CONCLUIDA"}' "$TOKEN_EU"
testar "PUT /paradas-os/$PARADA_MINHA (motorista) -> 403" 403 PUT "/paradas-os/$PARADA_MINHA" "{\"osId\":$MINHA,\"ordemParada\":1,\"localParada\":\"X\"}" "$TOKEN_EU"

resumo
