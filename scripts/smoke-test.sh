#!/bin/bash
# smoke test da API: dispara todas as requisicoes da colecao, na ordem, e mostra OK/FAIL de cada uma.
#
# como usar (com o backend de pe):
#   bash scripts/smoke-test.sh
#   AJT_URL=http://localhost:8080 AJT_USER=admin AJT_PASS=minhaSenha bash scripts/smoke-test.sh
#
# requer: bash, curl e jq. cria dados de teste e apaga tudo no final.
# usa o login do ADMIN — se o admin ainda estiver com a senha do seed (trocarSenha=true),
# o teste funciona igual, porque o backend so avisa (quem forca a troca e o front).
set -uo pipefail
BASE="${AJT_URL:-http://localhost:8080}"
AJT_USER="${AJT_USER:-admin}"
AJT_PASS="${AJT_PASS:-admin123}"
PASS=0
FAIL=0

req() {
  local desc="$1" expected="$2" method="$3" path="$4" body="${5:-}" auth="${6:-yes}"
  local hdr=()
  [ "$auth" = "yes" ] && hdr+=(-H "Authorization: Bearer $TOKEN")
  [ -n "$body" ] && hdr+=(-H "Content-Type: application/json")

  if [ -n "$body" ]; then
    resp=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" "${hdr[@]}" -d "$body")
  else
    resp=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" "${hdr[@]}")
  fi
  status=$(echo "$resp" | tail -n1)
  respBody=$(echo "$resp" | sed '$d')

  if [ "$status" = "$expected" ]; then
    echo "[OK]   $desc -> $status"
    PASS=$((PASS+1))
  else
    echo "[FAIL] $desc -> esperado $expected, veio $status"
    echo "       body: $(echo "$respBody" | head -c 300)"
    FAIL=$((FAIL+1))
  fi
  LAST_BODY="$respBody"
}

echo "=================== AUTENTICACAO ==================="
req "Login (admin)" 200 POST "/api/auth/login" "{\"username\":\"$AJT_USER\",\"senha\":\"$AJT_PASS\"}" no
TOKEN=$(echo "$LAST_BODY" | jq -r '.token')

req "Eu (sessao atual)" 200 GET "/api/auth/me"
req "Login sem token (401 esperado)" 401 GET "/api/transfers" "" no

echo "=================== USUARIOS (ADMIN) ==================="
req "Listar (paginado)" 200 GET "/api/usuarios?page=0&size=10&sort=id,desc"
req "Criar" 201 POST "/api/usuarios" '{"nome":"Maria Souza","username":"maria.souza","senha":"senha12345","role":"ATENDENTE"}'
USUARIO_ID=$(echo "$LAST_BODY" | jq -r '.id')
req "Buscar por ID" 200 GET "/api/usuarios/$USUARIO_ID"
req "Buscar por username" 200 GET "/api/usuarios/buscar?username=maria.souza"
req "Atualizar (PUT)" 200 PUT "/api/usuarios/$USUARIO_ID" '{"nome":"Maria Souza Lima","username":"maria.souza","role":"GERENTE"}'
req "Atualizar parcial (PATCH) - desativar" 200 PATCH "/api/usuarios/$USUARIO_ID" '{"ativo":false}'
req "Excluir" 204 DELETE "/api/usuarios/$USUARIO_ID"

echo "=================== MOTORISTAS ==================="
req "Listar (paginado)" 200 GET "/api/motoristas?page=0&size=10"
req "Criar" 201 POST "/api/motoristas" '{"nome":"Carlos Andrade","cnh":"12345678901","telefone":"(11) 98888-7777","latitudeAtual":-23.5505,"longitudeAtual":-46.6333}'
MOTORISTA_ID=$(echo "$LAST_BODY" | jq -r '.id')
req "Buscar por ID" 200 GET "/api/motoristas/$MOTORISTA_ID"
req "Buscar por CNH" 200 GET "/api/motoristas/buscar?cnh=12345678901"
req "Atualizar (PUT)" 200 PUT "/api/motoristas/$MOTORISTA_ID" '{"nome":"Carlos Andrade Silva","cnh":"12345678901","telefone":"(11) 98888-0000"}'
req "Atualizar parcial (PATCH)" 200 PATCH "/api/motoristas/$MOTORISTA_ID" '{"latitudeAtual":-23.5,"longitudeAtual":-46.6}'

echo "=================== VEICULOS ==================="
req "Listar (paginado)" 200 GET "/api/veiculos?page=0&size=10"
req "Criar" 201 POST "/api/veiculos" '{"label":"Van 01","placa":"ABC1D23","capacidade":15,"tipo":"VAN","marca":"Mercedes-Benz"}'
VEICULO_ID=$(echo "$LAST_BODY" | jq -r '.id')
req "Buscar por ID" 200 GET "/api/veiculos/$VEICULO_ID"
req "Buscar por placa" 200 GET "/api/veiculos/buscar?placa=ABC1D23"
req "Atualizar (PUT)" 200 PUT "/api/veiculos/$VEICULO_ID" '{"label":"Van 01 - Renovada","placa":"ABC1D23","capacidade":16,"tipo":"VAN","marca":"Mercedes-Benz"}'
req "Atualizar parcial (PATCH)" 200 PATCH "/api/veiculos/$VEICULO_ID" '{"capacidade":18}'

echo "=================== PASSAGEIROS ==================="
req "Listar (paginado)" 200 GET "/api/passageiros?page=0&size=10"
req "Criar" 201 POST "/api/passageiros" '{"nome":"John Doe","tipoDocumento":"PASSAPORTE","documento":"XP9988776","nacionalidade":"Americana"}'
PASSAGEIRO_ID=$(echo "$LAST_BODY" | jq -r '.id')
req "Buscar por ID" 200 GET "/api/passageiros/$PASSAGEIRO_ID"
req "Buscar por nacionalidade" 200 GET "/api/passageiros/buscar?nacionalidade=Americana&page=0&size=10"
req "Atualizar (PUT)" 200 PUT "/api/passageiros/$PASSAGEIRO_ID" '{"nome":"John Doe Jr","tipoDocumento":"PASSAPORTE","documento":"XP9988776","nacionalidade":"Americana"}'
req "Atualizar parcial (PATCH)" 200 PATCH "/api/passageiros/$PASSAGEIRO_ID" '{"nacionalidade":"Canadense"}'

echo "=================== TRANSFERS ==================="
req "Listar (paginado)" 200 GET "/api/transfers?page=0&size=10"
req "Criar (moeda estrangeira - Feign)" 201 POST "/api/transfers" '{"dataTransfer":"2026-09-25","horaTransfer":"14:30:00","origem":"Aeroporto de Guarulhos (GRU)","destino":"Hotel Copacabana Palace","status":"CONFIRMADO","valorOriginal":100.00,"moedaOrigem":"USD","passageiroIds":['"$PASSAGEIRO_ID"']}'
TRANSFER_ID=$(echo "$LAST_BODY" | jq -r '.id')
echo "       valorBase convertido: $(echo "$LAST_BODY" | jq -r '.valorBase')"
req "Buscar por ID" 200 GET "/api/transfers/$TRANSFER_ID"
echo "       passageiroIds: $(echo "$LAST_BODY" | jq -c '.passageiroIds')"
req "Listar passageiros do transfer" 200 GET "/api/transfers/$TRANSFER_ID/passageiros"
echo "       passageiros: $(echo "$LAST_BODY" | jq -r '[.[].nome] | join(", ")')"
req "Buscar por status" 200 GET "/api/transfers/buscar?status=CONFIRMADO&page=0&size=10"
req "Atualizar (PUT)" 200 PUT "/api/transfers/$TRANSFER_ID" '{"dataTransfer":"2026-09-25","horaTransfer":"15:00:00","origem":"Aeroporto de Guarulhos (GRU)","destino":"Hotel Fasano","status":"EM_ANDAMENTO","valorBase":350.00,"moedaOrigem":"BRL"}'
req "Atualizar parcial (PATCH) - concluir" 200 PATCH "/api/transfers/$TRANSFER_ID" '{"status":"CONCLUIDO"}'

echo "=================== PONTOS DE COLETA ==================="
req "Listar (paginado)" 200 GET "/api/pontos-coleta?page=0&size=10"
req "Criar" 201 POST "/api/pontos-coleta" "{\"transferId\":$TRANSFER_ID,\"localColeta\":\"Portao 3\",\"ordemParada\":1,\"horarioPrevisto\":\"14:00:00\",\"latitude\":-23.4356,\"longitude\":-46.4731}"
PONTO_ID=$(echo "$LAST_BODY" | jq -r '.id')
req "Buscar por ID" 200 GET "/api/pontos-coleta/$PONTO_ID"
req "Listar por Transfer" 200 GET "/api/pontos-coleta/transfer/$TRANSFER_ID"
req "Atualizar (PUT)" 200 PUT "/api/pontos-coleta/$PONTO_ID" "{\"transferId\":$TRANSFER_ID,\"localColeta\":\"Portao 5\",\"ordemParada\":1,\"horarioPrevisto\":\"14:10:00\",\"latitude\":-23.4356,\"longitude\":-46.4731}"
req "Atualizar parcial (PATCH)" 200 PATCH "/api/pontos-coleta/$PONTO_ID" '{"horarioPrevisto":"14:20:00"}'

echo "=================== ORDENS DE SERVICO ==================="
req "Listar (paginado)" 200 GET "/api/ordens-servico?page=0&size=10"
req "Criar" 201 POST "/api/ordens-servico" "{\"dataServico\":\"2026-09-25\",\"motoristaId\":$MOTORISTA_ID,\"veiculoId\":$VEICULO_ID}"
OS_ID=$(echo "$LAST_BODY" | jq -r '.id')
req "Buscar por ID" 200 GET "/api/ordens-servico/$OS_ID"
req "Buscar por status" 200 GET "/api/ordens-servico/buscar?status=ABERTA&page=0&size=10"
req "Ligar o transfer a OS (PATCH osId)" 200 PATCH "/api/transfers/$TRANSFER_ID" "{\"osId\":$OS_ID}"
echo "       osId do transfer: $(echo "$LAST_BODY" | jq -r '.osId')"
req "Atualizar (PUT)" 200 PUT "/api/ordens-servico/$OS_ID" "{\"dataServico\":\"2026-09-26\",\"motoristaId\":$MOTORISTA_ID,\"veiculoId\":$VEICULO_ID,\"status\":\"EM_ANDAMENTO\"}"
req "Atualizar parcial (PATCH) - finalizar" 200 PATCH "/api/ordens-servico/$OS_ID" '{"status":"FINALIZADA"}'

echo "=================== PARADAS DE OS ==================="
req "Listar (paginado)" 200 GET "/api/paradas-os?page=0&size=10"
req "Criar (embarque, com transfer vinculado)" 201 POST "/api/paradas-os" "{\"osId\":$OS_ID,\"ordemParada\":1,\"localParada\":\"Portao 3\",\"latitude\":-23.4356,\"longitude\":-46.4731,\"horarioPrevisto\":\"14:00:00\",\"acao\":\"EMBARQUE\",\"transferIds\":[$TRANSFER_ID]}"
PARADA_ID=$(echo "$LAST_BODY" | jq -r '.id')
req "Buscar por ID" 200 GET "/api/paradas-os/$PARADA_ID"
req "Listar por Ordem de Servico" 200 GET "/api/paradas-os/ordem-servico/$OS_ID"
req "Atualizar (PUT)" 200 PUT "/api/paradas-os/$PARADA_ID" "{\"osId\":$OS_ID,\"ordemParada\":1,\"localParada\":\"Portao 7\",\"latitude\":-23.4,\"longitude\":-46.4,\"acao\":\"DESEMBARQUE\",\"statusParada\":\"EM_ANDAMENTO\",\"transferIds\":[$TRANSFER_ID]}"
req "Atualizar parcial (PATCH) - MOTORISTA so troca status" 200 PATCH "/api/paradas-os/$PARADA_ID" '{"statusParada":"CONCLUIDA"}'

echo "=================== COTACAO (Feign) ==================="
req "Cotacao USD -> BRL" 200 GET "/api/cotacao?de=USD&para=BRL"
req "Cotacao EUR -> BRL" 200 GET "/api/cotacao?de=EUR&para=BRL"
req "Codigo de moeda invalido (400 esperado)" 400 GET "/api/cotacao?de=US&para=BRL"

echo "=================== AUDITORIA (ADMIN/GERENTE) ==================="
req "Auditoria dos transfers" 200 GET "/api/auditoria?tabela=transfers&page=0&size=5"
echo "       ultimo registro: $(echo "$LAST_BODY" | jq -r '.conteudo[0].mensagem')"
req "Auditoria das ordens de servico" 200 GET "/api/auditoria?tabela=ordens_servico&page=0&size=5"

echo "=================== DEMONSTRACAO DE ERROS ==================="
req "Passageiro inexistente no transfer (404)" 404 POST "/api/transfers" '{"dataTransfer":"2026-09-25","horaTransfer":"14:30:00","origem":"A","destino":"B","passageiroIds":[999999]}'
req "Status invalido - enum (400)" 400 POST "/api/transfers" '{"dataTransfer":"2026-09-25","horaTransfer":"14:30:00","origem":"A","destino":"B","status":"XPTO"}'
req "Campo obrigatorio ausente (400)" 400 POST "/api/motoristas" '{"nome":"","cnh":""}'
req "ID inexistente (404)" 404 GET "/api/motoristas/999999"

echo "=================== LIMPEZA (exclusoes no fim, pra nao quebrar FK antes da hora) ==================="
req "Excluir Ponto de Coleta" 204 DELETE "/api/pontos-coleta/$PONTO_ID"
req "Excluir Parada de OS" 204 DELETE "/api/paradas-os/$PARADA_ID"
req "Excluir Transfer" 204 DELETE "/api/transfers/$TRANSFER_ID"
req "Excluir Ordem de Servico" 204 DELETE "/api/ordens-servico/$OS_ID"
req "Excluir Veiculo" 204 DELETE "/api/veiculos/$VEICULO_ID"
req "Excluir Motorista" 204 DELETE "/api/motoristas/$MOTORISTA_ID"
req "Excluir Passageiro" 204 DELETE "/api/passageiros/$PASSAGEIRO_ID"

echo ""
echo "=================== RESUMO ==================="
echo "OK: $PASS   FALHOU: $FAIL"
