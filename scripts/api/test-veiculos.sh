#!/usr/bin/env bash
# o que testa: CRUD de /api/veiculos (POST, GET lista, GET id, GET buscar por placa, PUT,
# PATCH, DELETE e GET depois do delete) e os erros: 400 corpo invalido, 404 id inexistente,
# 400 placa duplicada e 409 ao excluir veiculo em uso por uma OS.
# como rodar: bash scripts/api/test-veiculos.sh (api de pe; ver common.sh).
# por que existe: confere o contrato http do cadastro de veiculos contra a api de verdade.
source "$(dirname "$0")/common.sh"
echo "=== Veiculos ==="

# placa tem no maximo 10 caracteres: os ultimos digitos do sufixo garantem que nao repete
PLACA="V${SUFIXO: -9}"

testar "POST /veiculos" 201 POST "/veiculos" "{\"label\":\"Van 01\",\"placa\":\"$PLACA\",\"capacidade\":15,\"tipo\":\"VAN\",\"marca\":\"Mercedes-Benz\"}"
ID=$(id_de "$BODY")
apagar_no_fim "/veiculos/$ID"

testar "GET /veiculos" 200 GET "/veiculos"
testar "GET /veiculos?size=2&sort=id,desc (paginado)" 200 GET "/veiculos?size=2&sort=id,desc"
check_valor "pagina com tamanho 2" "2" "$(valor_de "$BODY" tamanho)"
testar "GET /veiculos/$ID" 200 GET "/veiculos/$ID"
testar "GET /veiculos/buscar?placa=" 200 GET "/veiculos/buscar?placa=$PLACA"
check_valor "busca por placa acha o veiculo criado" "$ID" "$(id_de "$BODY")"

testar "PUT /veiculos/$ID" 200 PUT "/veiculos/$ID" "{\"label\":\"Van 01 Renovada\",\"placa\":\"$PLACA\",\"capacidade\":16}"
testar "PATCH /veiculos/$ID" 200 PATCH "/veiculos/$ID" '{"capacidade":18}'
check_valor "PATCH troca so a capacidade" "18" "$(valor_de "$BODY" capacidade)"

# caminhos tristes
testar "POST /veiculos (capacidade zero) -> 400" 400 POST "/veiculos" "{\"label\":\"Van\",\"placa\":\"W${SUFIXO: -9}\",\"capacidade\":0}"
testar "POST /veiculos (placa maior que 10) -> 400" 400 POST "/veiculos" '{"label":"Van","placa":"ABCDEFGHIJK","capacidade":10}'
testar "POST /veiculos (capacidade em texto) -> 400" 400 POST "/veiculos" "{\"label\":\"Van\",\"placa\":\"X${SUFIXO: -9}\",\"capacidade\":\"quinze\"}"
testar "PATCH /veiculos/$ID (placa vazia) -> 400" 400 PATCH "/veiculos/$ID" '{"placa":""}'
testar "POST /veiculos (placa duplicada) -> 400" 400 POST "/veiculos" "{\"label\":\"Outra\",\"placa\":\"$PLACA\",\"capacidade\":10}"
testar "GET /veiculos/999999999 -> 404" 404 GET "/veiculos/999999999"
testar "PUT /veiculos/999999999 -> 404" 404 PUT "/veiculos/999999999" "{\"label\":\"Van\",\"placa\":\"Y${SUFIXO: -9}\",\"capacidade\":10}"
testar "PATCH /veiculos/999999999 -> 404" 404 PATCH "/veiculos/999999999" '{"marca":"Fiat"}'
testar "DELETE /veiculos/999999999 -> 404" 404 DELETE "/veiculos/999999999"
testar "GET /veiculos/buscar?placa=inexistente -> 404" 404 GET "/veiculos/buscar?placa=ZZ${SUFIXO: -6}"
testar "GET /veiculos/buscar (sem placa) -> 400" 400 GET "/veiculos/buscar"

criar "/ordens-servico" "{\"dataServico\":\"2030-01-10\",\"veiculoId\":$ID}"
testar "DELETE /veiculos/$ID (em uso por OS) -> 409" 409 DELETE "/veiculos/$ID"
req DELETE "/ordens-servico/$NOVO_ID"

testar "DELETE /veiculos/$ID" 204 DELETE "/veiculos/$ID"
testar "GET /veiculos/$ID (depois do delete) -> 404" 404 GET "/veiculos/$ID"

resumo
