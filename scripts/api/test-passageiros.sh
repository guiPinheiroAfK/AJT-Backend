#!/usr/bin/env bash
# o que testa: CRUD de /api/passageiros (POST, GET lista, GET id, GET buscar por
# nacionalidade, PUT, PATCH, DELETE e GET depois do delete) e os erros: 400 corpo invalido,
# 400 CPF invalido (regra do service), 404 id inexistente e 400 id com tipo errado.
# como rodar: bash scripts/api/test-passageiros.sh (api de pe; ver common.sh).
# por que existe: confere o contrato http do cadastro de passageiros e que o documento
# volta em claro pra quem pode ver (ele fica cifrado no banco).
source "$(dirname "$0")/common.sh"
echo "=== Passageiros ==="

NOME="Ana $SUFIXO"
CPF_VALIDO="529.982.247-25"

testar "POST /passageiros (CPF valido)" 201 POST "/passageiros" "{\"nome\":\"$NOME\",\"tipoDocumento\":\"CPF\",\"documento\":\"$CPF_VALIDO\",\"nacionalidade\":\"Brasileira\"}"
ID=$(id_de "$BODY")
apagar_no_fim "/passageiros/$ID"
check_valor "documento volta em claro pro admin" "$CPF_VALIDO" "$(valor_de "$BODY" documento)"

testar "GET /passageiros" 200 GET "/passageiros"
testar "GET /passageiros?nome= (filtro por parte do nome)" 200 GET "/passageiros?nome=$SUFIXO"
check_valor "filtro por nome acha 1" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /passageiros/$ID" 200 GET "/passageiros/$ID"
testar "GET /passageiros/buscar?nacionalidade=" 200 GET "/passageiros/buscar?nacionalidade=brasileira"

testar "PUT /passageiros/$ID" 200 PUT "/passageiros/$ID" "{\"nome\":\"$NOME Souza\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"XP$SUFIXO\",\"nacionalidade\":\"Brasileira\"}"
testar "PATCH /passageiros/$ID" 200 PATCH "/passageiros/$ID" '{"nacionalidade":"Argentina"}'
check_valor "PATCH troca so a nacionalidade" "Argentina" "$(valor_de "$BODY" nacionalidade)"

# caminhos tristes
testar "POST /passageiros (sem nome) -> 400" 400 POST "/passageiros" '{"tipoDocumento":"CPF","documento":"1"}'
testar "POST /passageiros (CPF invalido) -> 400" 400 POST "/passageiros" '{"nome":"Ana","tipoDocumento":"CPF","documento":"123"}'
testar "POST /passageiros (json malformado) -> 400" 400 POST "/passageiros" '{"nome":'
testar "PATCH /passageiros/$ID (nome vazio) -> 400" 400 PATCH "/passageiros/$ID" '{"nome":""}'
testar "GET /passageiros/abc (id com tipo errado) -> 400" 400 GET "/passageiros/abc"
testar "GET /passageiros/999999999 -> 404" 404 GET "/passageiros/999999999"
testar "PUT /passageiros/999999999 -> 404" 404 PUT "/passageiros/999999999" '{"nome":"X","tipoDocumento":"RG","documento":"1"}'
testar "PATCH /passageiros/999999999 -> 404" 404 PATCH "/passageiros/999999999" '{"nome":"X"}'
testar "DELETE /passageiros/999999999 -> 404" 404 DELETE "/passageiros/999999999"
testar "GET /passageiros/buscar (sem nacionalidade) -> 400" 400 GET "/passageiros/buscar"

testar "DELETE /passageiros/$ID" 204 DELETE "/passageiros/$ID"
testar "GET /passageiros/$ID (depois do delete) -> 404" 404 GET "/passageiros/$ID"

resumo
