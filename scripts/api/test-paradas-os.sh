#!/usr/bin/env bash
# o que testa: CRUD de /api/paradas-os (POST, GET lista, GET id, GET por OS, PUT, PATCH,
# DELETE e GET depois do delete), o vinculo da parada com transfers, a ordem das paradas
# da OS e os erros 400 e 404.
# como rodar: bash scripts/api/test-paradas-os.sh (api de pe; ver common.sh).
# por que existe: as paradas sao o roteiro da OS; o que o motorista ve e marca na rua.
source "$(dirname "$0")/common.sh"
echo "=== Paradas da OS ==="

DIA="2030-04-08"
criar "/ordens-servico" "{\"dataServico\":\"$DIA\"}"
OS=$NOVO_ID
criar "/transfers" "{\"dataTransfer\":\"$DIA\",\"horaTransfer\":\"14:30:00\",\"origem\":\"GRU $SUFIXO\",\"destino\":\"Hotel\"}"
TRANSFER=$NOVO_ID

testar "POST /paradas-os (ordem 2, com transfer)" 201 POST "/paradas-os" "{\"osId\":$OS,\"ordemParada\":2,\"localParada\":\"Hotel\",\"acao\":\"DESEMBARQUE\",\"transferIds\":[$TRANSFER]}"
ID=$(id_de "$BODY")
apagar_no_fim "/paradas-os/$ID"
check_valor "parada nasce PENDENTE" "PENDENTE" "$(valor_de "$BODY" statusParada)"
criar "/paradas-os" "{\"osId\":$OS,\"ordemParada\":1,\"localParada\":\"Portao 3\",\"latitude\":-23.43,\"longitude\":-46.47,\"horarioPrevisto\":\"14:00:00\",\"acao\":\"EMBARQUE\",\"transferIds\":[$TRANSFER]}"

testar "GET /paradas-os" 200 GET "/paradas-os"
testar "GET /paradas-os/$ID" 200 GET "/paradas-os/$ID"
testar "GET /paradas-os/ordem-servico/$OS" 200 GET "/paradas-os/ordem-servico/$OS"
check_valor "paradas da OS vem na ordem (Portao 3 primeiro)" "Portao 3" "$(valor_de "$BODY" localParada)"

testar "PUT /paradas-os/$ID" 200 PUT "/paradas-os/$ID" "{\"osId\":$OS,\"ordemParada\":2,\"localParada\":\"Hotel Fasano\",\"acao\":\"DESEMBARQUE\",\"statusParada\":\"EM_ANDAMENTO\",\"transferIds\":[$TRANSFER]}"
check_valor "PUT respeita o status informado" "EM_ANDAMENTO" "$(valor_de "$BODY" statusParada)"
testar "PATCH /paradas-os/$ID (status)" 200 PATCH "/paradas-os/$ID" '{"statusParada":"CONCLUIDA"}'
check_valor "PATCH troca o status" "CONCLUIDA" "$(valor_de "$BODY" statusParada)"
testar "PATCH /paradas-os/$ID (lista vazia de transfers)" 200 PATCH "/paradas-os/$ID" '{"transferIds":[]}'

# caminhos tristes
testar "POST /paradas-os (sem OS) -> 400" 400 POST "/paradas-os" '{"ordemParada":1,"localParada":"A"}'
testar "POST /paradas-os (ordem negativa) -> 400" 400 POST "/paradas-os" "{\"osId\":$OS,\"ordemParada\":-1,\"localParada\":\"A\"}"
testar "POST /paradas-os (acao fora do enum) -> 400" 400 POST "/paradas-os" "{\"osId\":$OS,\"ordemParada\":1,\"localParada\":\"A\",\"acao\":\"PULAR\"}"
testar "POST /paradas-os (OS inexistente) -> 404" 404 POST "/paradas-os" '{"osId":999999999,"ordemParada":1,"localParada":"A"}'
testar "POST /paradas-os (transfer inexistente) -> 404" 404 POST "/paradas-os" "{\"osId\":$OS,\"ordemParada\":3,\"localParada\":\"A\",\"transferIds\":[999999999]}"
# pra ADMIN/GERENTE a OS inexistente devolve lista vazia (so o motorista recebe 404, ver test-motorista-minhas-os.sh)
testar "GET /paradas-os/ordem-servico/999999999 (admin, OS inexistente)" 200 GET "/paradas-os/ordem-servico/999999999"
check_valor "OS inexistente devolve lista vazia" "[]" "$BODY"
testar "GET /paradas-os/999999999 -> 404" 404 GET "/paradas-os/999999999"
testar "PUT /paradas-os/999999999 -> 404" 404 PUT "/paradas-os/999999999" "{\"osId\":$OS,\"ordemParada\":1,\"localParada\":\"A\"}"
testar "PATCH /paradas-os/999999999 -> 404" 404 PATCH "/paradas-os/999999999" '{"statusParada":"CONCLUIDA"}'
testar "DELETE /paradas-os/999999999 -> 404" 404 DELETE "/paradas-os/999999999"

testar "DELETE /paradas-os/$ID" 204 DELETE "/paradas-os/$ID"
testar "GET /paradas-os/$ID (depois do delete) -> 404" 404 GET "/paradas-os/$ID"

resumo
