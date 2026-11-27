#!/bin/bash
# seed de demonstracao: popula a API com um conjunto completo de dados mock
# (usuarios de cada perfil, motoristas, veiculos, passageiros, transfers em todos os
# status, pontos de coleta, ordens de servico com paradas, em datas proximas de hoje).
#
# os dados entram pela propria API, entao documentos saem cifrados, a auditoria e
# gravada e as regras de negocio valem (transfer entra em OS = CONFIRMADO, etc).
#
# como usar (com a API no ar):
#   bash scripts/seed-demo.sh
#   AJT_URL=http://localhost:8080 AJT_PASS=<senha do admin> bash scripts/seed-demo.sh
#
# requer: bash, curl e jq. nao apaga nada: rodar duas vezes gera dados duplicados
# (username, CNH, placa e documento repetidos sao recusados pela API e aparecem como FAIL).
set -uo pipefail
BASE="${AJT_URL:-http://localhost:9090}"
AJT_USER="${AJT_USER:-admin}"
AJT_PASS="${AJT_PASS:-admin123}"
DEMO_PASS="${AJT_DEMO_PASS:-demo123456}"
FAILS=0
CORPO_TMP=$(mktemp)
trap 'rm -f "$CORPO_TMP"' EXIT

hoje=$(date +%F)
dia() { date -d "$hoje $1 days" +%F; } # dia -1, dia 0, dia +2 (relativo a hoje)

# chamada HTTP: guarda o corpo em $RESP e o status em $CODE; avisa se nao for 2xx
api() {
  local metodo="$1" caminho="$2" corpo="${3:-}"
  local args=(-s -w "\n%{http_code}" -X "$metodo" "$BASE$caminho")
  [ -n "$TOKEN" ] && args+=(-H "Authorization: Bearer $TOKEN")
  # no Git Bash (Windows) acento em -d "$corpo" chega errado na API (400): manda o corpo por arquivo
  if [ -n "$corpo" ]; then
    printf '%s' "$corpo" > "$CORPO_TMP"
    args+=(-H "Content-Type: application/json" --data-binary "@$CORPO_TMP")
  fi
  local saida
  saida=$(curl "${args[@]}")
  CODE=$(echo "$saida" | tail -n1)
  RESP=$(echo "$saida" | sed '$d')
  if [[ "$CODE" != 2* ]]; then
    # stderr: a funcao criar() captura o stdout para pegar o id, e a falha nao pode virar id
    echo "[FAIL] $metodo $caminho -> $CODE: $(echo "$RESP" | head -c 200)" >&2
    FAILS=$((FAILS+1))
    return 1
  fi
  return 0
}

# cria e devolve o id (ou vazio se falhar)
criar() { api POST "$1" "$2" && echo "$RESP" | jq -r '.id'; }

TOKEN=""
api POST /api/auth/login "$(jq -n --arg u "$AJT_USER" --arg s "$AJT_PASS" '{username:$u, senha:$s}')" || { echo "login falhou, API no ar em $BASE?"; exit 1; }
TOKEN=$(echo "$RESP" | jq -r '.token')
echo "login ok ($AJT_USER) em $BASE"

echo "== usuarios"
api POST /api/usuarios "$(jq -n --arg p "$DEMO_PASS" '{nome:"Marina Lima", username:"gerente.marina", senha:$p, role:"GERENTE"}')"
api POST /api/usuarios "$(jq -n --arg p "$DEMO_PASS" '{nome:"Ana Souza", username:"atendente.ana", senha:$p, role:"ATENDENTE"}')"
api POST /api/usuarios "$(jq -n --arg p "$DEMO_PASS" '{nome:"Bruno Costa", username:"atendente.bruno", senha:$p, role:"ATENDENTE"}')"

echo "== motoristas"
MOTORISTAS=(
  '{"nome":"Carlos Andrade","cnh":"04512389761","telefone":"(11) 98888-1001","latitudeAtual":-23.5505,"longitudeAtual":-46.6333}'
  '{"nome":"Paulo Ribeiro","cnh":"05523478812","telefone":"(11) 98888-1002","latitudeAtual":-23.4356,"longitudeAtual":-46.4731}'
  '{"nome":"Rafael Teixeira","cnh":"06634589023","telefone":"(11) 98888-1003","latitudeAtual":-23.6100,"longitudeAtual":-46.6600}'
  '{"nome":"José Almeida","cnh":"07745690134","telefone":"(11) 98888-1004","latitudeAtual":-23.5600,"longitudeAtual":-46.6500}'
  '{"nome":"Marcos Pereira","cnh":"08856701245","telefone":"(11) 98888-1005","latitudeAtual":-23.5200,"longitudeAtual":-46.7000}'
  '{"nome":"Eduardo Nunes","cnh":"09967812356","telefone":"(11) 98888-1006","latitudeAtual":-23.4800,"longitudeAtual":-46.5500}'
)
MOT_IDS=()
for m in "${MOTORISTAS[@]}"; do MOT_IDS+=("$(criar /api/motoristas "$m")"); done

echo "== usuarios MOTORISTA (ligados aos 3 primeiros motoristas)"
api POST /api/usuarios "$(jq -n --arg p "$DEMO_PASS" --argjson m "${MOT_IDS[0]}" '{nome:"Carlos Andrade", username:"motorista.carlos", senha:$p, role:"MOTORISTA", motoristaId:$m}')"
api POST /api/usuarios "$(jq -n --arg p "$DEMO_PASS" --argjson m "${MOT_IDS[1]}" '{nome:"Paulo Ribeiro", username:"motorista.paulo", senha:$p, role:"MOTORISTA", motoristaId:$m}')"
api POST /api/usuarios "$(jq -n --arg p "$DEMO_PASS" --argjson m "${MOT_IDS[2]}" '{nome:"Rafael Teixeira", username:"motorista.rafael", senha:$p, role:"MOTORISTA", motoristaId:$m}')"

echo "== veiculos"
VEICULOS=(
  '{"label":"Van 01","placa":"RIO2A18","capacidade":15,"tipo":"VAN","marca":"Mercedes-Benz"}'
  '{"label":"Van 02","placa":"FKT3B21","capacidade":15,"tipo":"VAN","marca":"Renault"}'
  '{"label":"Micro 01","placa":"GHW4C55","capacidade":28,"tipo":"MICRO-ONIBUS","marca":"Volkswagen"}'
  '{"label":"Sedan 01","placa":"JQA5D09","capacidade":4,"tipo":"SEDAN","marca":"Toyota"}'
  '{"label":"SUV 01","placa":"LMB6E14","capacidade":6,"tipo":"SUV","marca":"Chevrolet"}'
  '{"label":"Van 03","placa":"NPC7F32","capacidade":18,"tipo":"VAN","marca":"Mercedes-Benz"}'
)
VEI_IDS=()
for v in "${VEICULOS[@]}"; do VEI_IDS+=("$(criar /api/veiculos "$v")"); done

echo "== passageiros"
PASSAGEIROS=(
  '{"nome":"John Doe","tipoDocumento":"PASSAPORTE","documento":"XP9988776","nacionalidade":"Americana"}'
  '{"nome":"Emma Watson","tipoDocumento":"PASSAPORTE","documento":"GB1234567","nacionalidade":"Britânica"}'
  '{"nome":"Lucas Müller","tipoDocumento":"PASSAPORTE","documento":"DE7654321","nacionalidade":"Alemã"}'
  '{"nome":"Sophie Martin","tipoDocumento":"PASSAPORTE","documento":"FR1122334","nacionalidade":"Francesa"}'
  '{"nome":"Hiroshi Tanaka","tipoDocumento":"PASSAPORTE","documento":"JP5566778","nacionalidade":"Japonesa"}'
  '{"nome":"Maria Gonzalez","tipoDocumento":"PASSAPORTE","documento":"AR9988001","nacionalidade":"Argentina"}'
  '{"nome":"Carlos Mendes","tipoDocumento":"CPF","documento":"529.982.247-25","nacionalidade":"Brasileira"}'
  '{"nome":"Juliana Rocha","tipoDocumento":"CPF","documento":"100.000.001-08","nacionalidade":"Brasileira"}'
  '{"nome":"Peter Schmidt","tipoDocumento":"PASSAPORTE","documento":"CH4455667","nacionalidade":"Suíça"}'
  '{"nome":"Olivia Brown","tipoDocumento":"PASSAPORTE","documento":"US1357924","nacionalidade":"Americana"}'
  '{"nome":"Giuseppe Rossi","tipoDocumento":"PASSAPORTE","documento":"IT8642097","nacionalidade":"Italiana"}'
  '{"nome":"Yuki Sato","tipoDocumento":"PASSAPORTE","documento":"JP1029384","nacionalidade":"Japonesa"}'
)
PAS_IDS=()
for p in "${PASSAGEIROS[@]}"; do PAS_IDS+=("$(criar /api/passageiros "$p")"); done

# transfers: dia|hora|origem|destino|valor|passageiros (indices)|OS (indice ou -)|status final
# OS: 0 = dia -1 (FINALIZADA), 1 = dia 0 (EM_ANDAMENTO), 2 = dia 0 (ABERTA),
#     3 = dia +1 (ABERTA), 4 = dia +2 (CANCELADA)
TRANSFERS=(
  '-1|06:30|Aeroporto Internacional de Guarulhos (GRU)|Hotel Copacabana Palace|420.00|0,1|0|CONCLUIDO'
  '-1|08:15|Aeroporto de Congonhas (CGH)|Hotel Fasano São Paulo|310.00|2|0|CONCLUIDO'
  '-1|11:40|Hotel Unique|Rodoviária do Tietê|180.00|3,4|0|CONCLUIDO'
  '-1|15:00|Aeroporto Santos Dumont (SDU)|Hotel Sheraton Rio|290.00|5|0|CONCLUIDO'
  '0|07:00|Aeroporto de Guarulhos (GRU)|Hotel Maksoud Plaza|350.00|6,7|1|EM_ANDAMENTO'
  '0|09:30|Hotel Emiliano|Museu do Ipiranga|120.00|8|1|CONFIRMADO'
  '0|10:45|Estação da Luz|Hotel Renaissance|150.00|9,10|1|CONFIRMADO'
  '0|13:00|Aeroporto de Guarulhos (GRU)|Hotel Tivoli Mofarrej|360.00|11|2|CONFIRMADO'
  '0|14:30|Hotel Grand Hyatt|Parque Ibirapuera|95.00|0|2|CONFIRMADO'
  '0|16:00|Aeroporto de Congonhas (CGH)|Hotel Blue Tree Faria Lima|210.00|1,2|2|CONFIRMADO'
  '0|18:20|Shopping Iguatemi|Aeroporto de Guarulhos (GRU)|200.00|3|-|AGUARDANDO_OS'
  '0|11:00|Hotel Fasano São Paulo|Restaurante Figueira Rubaiyat|85.00|0|-|AGUARDANDO_OS'
  '1|06:45|Aeroporto de Guarulhos (GRU)|Hotel Pullman Vila Olímpia|330.00|4,5|3|CONFIRMADO'
  '1|09:00|Hotel JW Marriott|Hotel Tivoli|110.00|6|3|CONFIRMADO'
  '1|12:30|Rodoviária do Tietê|Hotel Ibis Paulista|140.00|7,8|3|CONFIRMADO'
  '1|17:00|Aeroporto de Viracopos (VCP)|Hotel Hilton Morumbi|280.00|9|-|AGUARDANDO_OS'
  '2|08:00|Aeroporto de Guarulhos (GRU)|Hotel Caesar Business|300.00|10|4|CONFIRMADO'
  '4|09:00|Aeroporto de Guarulhos (GRU)|Hotel Tivoli|300.00|11|-|AGUARDANDO_OS'
  '5|10:00|Hotel Unique|Aeroporto de Congonhas (CGH)|160.00|0,1|-|AGUARDANDO_OS'
  '-2|09:00|Aeroporto Internacional de Guarulhos (GRU)|Hotel Emiliano|250.00|5|-|CANCELADO'
  '-3|14:00|Hotel Maksoud Plaza|Aeroporto de Congonhas (CGH)|190.00|4|-|CANCELADO'
)

echo "== transfers (em OS, sem OS, cancelados)"
T_IDS=()
for linha in "${TRANSFERS[@]}"; do
  IFS='|' read -r off hora origem destino valor pas os_idx status_final <<< "$linha"
  ids=$(printf '%s\n' ${pas//,/ } | while read -r i; do echo "${PAS_IDS[$i]}"; done | jq -s -c '.')
  corpo=$(jq -n --arg data "$(dia "$off")" --arg hora "$hora:00" --arg origem "$origem" \
    --arg destino "$destino" --argjson valor "$valor" --argjson pas "$ids" \
    '{dataTransfer:$data, horaTransfer:$hora, origem:$origem, destino:$destino, valorBase:$valor, moedaOrigem:"BRL", passageiroIds:$pas}')
  id=$(criar /api/transfers "$corpo")
  T_IDS+=("$id")
  # o status final fica por conta do fluxo (OS, CONCLUIDO, CANCELADO) abaixo
done

# depois de criados, ligamos os transfers as OS e ajustamos o status
echo "== ordens de servico (dia, motorista, veiculo)"
OS_SPEC=(
  '-1|0|0|FINALIZADA'
  '0|1|1|EM_ANDAMENTO'
  '0|2|2|ABERTA'
  '1|3|3|ABERTA'
  '2|4|4|CANCELADA'
)
OS_IDS=()
for i in "${!OS_SPEC[@]}"; do
  IFS='|' read -r off m v status <<< "${OS_SPEC[$i]}"
  corpo=$(jq -n --arg data "$(dia "$off")" --argjson m "${MOT_IDS[$m]}" --argjson v "${VEI_IDS[$v]}" \
    '{dataServico:$data, motoristaId:$m, veiculoId:$v}')
  OS_IDS+=("$(criar /api/ordens-servico "$corpo")")
done

echo "== vinculo transfer -> OS e status dos transfers"
for i in "${!TRANSFERS[@]}"; do
  IFS='|' read -r off hora origem destino valor pas os_idx status_final <<< "${TRANSFERS[$i]}"
  tid="${T_IDS[$i]}"
  [ -z "$tid" ] && continue
  if [ "$os_idx" != "-" ]; then
    api PATCH "/api/transfers/$tid" "$(jq -n --argjson o "${OS_IDS[$os_idx]}" '{osId:$o}')"
  fi
  if [ "$status_final" = "CONCLUIDO" ] || [ "$status_final" = "CANCELADO" ] || [ "$status_final" = "EM_ANDAMENTO" ]; then
    api PATCH "/api/transfers/$tid" "$(jq -n --arg s "$status_final" '{status:$s}')"
  fi
done

echo "== pontos de coleta (alguns transfers)"
for i in 0 1 4 5 6 7 12 13 16; do
  IFS='|' read -r off hora origem destino valor pas os_idx status_final <<< "${TRANSFERS[$i]}"
  tid="${T_IDS[$i]}"
  [ -z "$tid" ] && continue
  criar /api/pontos-coleta "$(jq -n --argjson t "$tid" --arg local "Embarque $origem" \
    --arg hora "$(printf '%02d:%02d:00' $((10 + i % 8)) 30)" \
    '{transferId:$t, localColeta:$local, ordemParada:1, horarioPrevisto:$hora, latitude:-23.4356, longitude:-46.4731}')" >/dev/null
  criar /api/pontos-coleta "$(jq -n --argjson t "$tid" --arg local "Hotel de destino" \
    --arg hora "$(printf '%02d:%02d:00' $((11 + i % 8)) 10)" \
    '{transferId:$t, localColeta:$local, ordemParada:2, horarioPrevisto:$hora, latitude:-23.5630, longitude:-46.6540}')" >/dev/null
done

echo "== paradas de OS (embarque e desembarque com os transfers da OS)"
# para cada OS: parada 1 = EMBARQUE com os transfers de indice par, parada 2 = DESEMBARQUE com os impares
for os in 0 1 2 3 4; do
  os_id="${OS_IDS[$os]}"
  [ -z "$os_id" ] && continue
  grupo_par=(); grupo_impar=()
  for i in "${!TRANSFERS[@]}"; do
    IFS='|' read -r off hora origem destino valor pas os_idx status_final <<< "${TRANSFERS[$i]}"
    [ "$os_idx" = "$os" ] || continue
    [ -z "${T_IDS[$i]}" ] && continue
    if (( i % 2 == 0 )); then grupo_par+=("${T_IDS[$i]}"); else grupo_impar+=("${T_IDS[$i]}"); fi
  done
  for parada in 1 2; do
    if [ $parada = 1 ]; then ids=("${grupo_par[@]}"); acao="EMBARQUE"; local_p="Saída do aeroporto"; hr="08:00:00"
    else ids=("${grupo_impar[@]}"); acao="DESEMBARQUE"; local_p="Chegada no hotel"; hr="10:30:00"; fi
    [ ${#ids[@]} -eq 0 ] && continue
    ids_json=$(printf '%s\n' "${ids[@]}" | jq -s -c '.')
    corpo=$(jq -n --argjson os "$os_id" --argjson ord "$parada" --arg local "$local_p" --arg acao "$acao" \
      --arg hr "$hr" --argjson ids "$ids_json" \
      '{osId:$os, ordemParada:$ord, localParada:$local, latitude:-23.5505, longitude:-46.6333, horarioPrevisto:$hr, acao:$acao, transferIds:$ids}')
    pid=$(criar /api/paradas-os "$corpo")
    # paradas da OS finalizada ficam concluidas; a da OS em andamento, so a primeira
    if { [ "$os" = 0 ]; } || { [ "$os" = 1 ] && [ $parada = 1 ]; }; then
      [ -n "$pid" ] && api PATCH "/api/paradas-os/$pid" '{"statusParada":"CONCLUIDA"}'
    fi
  done
done

echo "== status final das ordens de servico"
for i in "${!OS_SPEC[@]}"; do
  IFS='|' read -r off m v status <<< "${OS_SPEC[$i]}"
  [ -z "${OS_IDS[$i]}" ] && continue
  [ "$status" = "ABERTA" ] && continue
  api PATCH "/api/ordens-servico/${OS_IDS[$i]}" "$(jq -n --arg s "$status" '{status:$s}')"
done

echo
echo "== resumo (totais na API)"
for recurso in usuarios motoristas veiculos passageiros transfers pontos-coleta ordens-servico paradas-os; do
  api GET "/api/$recurso?page=0&size=1" && echo "  $recurso: $(echo "$RESP" | jq -r '.totalElementos')"
done
echo
if [ "$FAILS" -gt 0 ]; then
  echo "seed terminou com $FAILS falha(s) (veja as linhas [FAIL] acima)"
  exit 1
fi
echo "seed concluido sem falhas"
echo "logins de demonstracao: admin / $AJT_PASS  e  gerente.marina, atendente.ana, atendente.bruno,"
echo "  motorista.carlos, motorista.paulo, motorista.rafael  (senha: $DEMO_PASS)"
