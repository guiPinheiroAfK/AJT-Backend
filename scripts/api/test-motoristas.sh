#!/usr/bin/env bash
# o que testa: CRUD de /api/motoristas (POST, GET lista, GET id, GET buscar por cnh, PUT,
# PATCH, DELETE e GET depois do delete) e os erros: 400 corpo invalido, 404 id inexistente,
# 400 cnh duplicada e 409 ao excluir motorista em uso por uma OS.
# como rodar: bash scripts/api/test-motoristas.sh (api de pe; ver common.sh).
# por que existe: confere o contrato http do cadastro de motoristas contra a api de verdade.
source "$(dirname "$0")/common.sh"
echo "=== Motoristas ==="

CNH="M${SUFIXO: -12}"

testar "POST /motoristas" 201 POST "/motoristas" "{\"nome\":\"Joao Silva\",\"cnh\":\"$CNH\",\"telefone\":\"11999999999\",\"latitudeAtual\":-23.55,\"longitudeAtual\":-46.63}"
ID=$(id_de "$BODY")
apagar_no_fim "/motoristas/$ID"
check_valor "POST /motoristas devolve versao 0" "0" "$(valor_de "$BODY" versao)"

testar "GET /motoristas" 200 GET "/motoristas"
testar "GET /motoristas?nome=joao (filtro)" 200 GET "/motoristas?nome=joao"
testar "GET /motoristas/$ID" 200 GET "/motoristas/$ID"
testar "GET /motoristas/buscar?cnh=" 200 GET "/motoristas/buscar?cnh=$CNH"
check_valor "busca por cnh acha o motorista criado" "$ID" "$(id_de "$BODY")"

testar "PUT /motoristas/$ID" 200 PUT "/motoristas/$ID" "{\"nome\":\"Joao Silva Jr\",\"cnh\":\"$CNH\",\"telefone\":\"11988888888\"}"
check_valor "PUT troca o nome" "Joao Silva Jr" "$(valor_de "$BODY" nome)"
testar "PATCH /motoristas/$ID" 200 PATCH "/motoristas/$ID" '{"telefone":"11977777777"}'
check_valor "PATCH troca so o telefone" "11977777777" "$(valor_de "$BODY" telefone)"

# caminhos tristes
testar "POST /motoristas (sem nome) -> 400" 400 POST "/motoristas" "{\"cnh\":\"X$SUFIXO\"}"
testar "POST /motoristas (latitude fora da faixa) -> 400" 400 POST "/motoristas" "{\"nome\":\"A\",\"cnh\":\"Y$SUFIXO\",\"latitudeAtual\":91}"
testar "PATCH /motoristas/$ID (cnh em branco) -> 400" 400 PATCH "/motoristas/$ID" '{"cnh":"  "}'
testar "POST /motoristas (cnh duplicada) -> 400" 400 POST "/motoristas" "{\"nome\":\"Outro\",\"cnh\":\"$CNH\"}"
testar "GET /motoristas/999999999 -> 404" 404 GET "/motoristas/999999999"
testar "PUT /motoristas/999999999 -> 404" 404 PUT "/motoristas/999999999" "{\"nome\":\"X\",\"cnh\":\"Z$SUFIXO\"}"
testar "PATCH /motoristas/999999999 -> 404" 404 PATCH "/motoristas/999999999" '{"nome":"X"}'
testar "DELETE /motoristas/999999999 -> 404" 404 DELETE "/motoristas/999999999"
testar "GET /motoristas/buscar?cnh=inexistente -> 404" 404 GET "/motoristas/buscar?cnh=nao-existe-$SUFIXO"
testar "GET /motoristas/buscar (sem cnh) -> 400" 400 GET "/motoristas/buscar"

# motorista em uso por uma OS nao pode ser excluido
criar "/ordens-servico" "{\"dataServico\":\"2030-01-10\",\"motoristaId\":$ID}"
testar "DELETE /motoristas/$ID (em uso por OS) -> 409" 409 DELETE "/motoristas/$ID"
req DELETE "/ordens-servico/$NOVO_ID"

testar "DELETE /motoristas/$ID" 204 DELETE "/motoristas/$ID"
testar "GET /motoristas/$ID (depois do delete) -> 404" 404 GET "/motoristas/$ID"

resumo
