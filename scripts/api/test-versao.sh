#!/usr/bin/env bash
# o que testa: o controle de concorrencia otimista (RNF008) em cada cadastro que usa a
# versao: motoristas, veiculos, passageiros, usuarios, pontos-coleta, paradas-os, transfers
# e ordens-servico. PATCH com a versao certa passa e sobe a versao; com versao velha
# devolve 409 (PATCH e PUT); sem versao no corpo nao confere (cliente antigo).
# como rodar: bash scripts/api/test-versao.sh (api de pe; ver common.sh).
# por que existe: duas pessoas editando o mesmo registro nao podem se sobrescrever calado.
source "$(dirname "$0")/common.sh"
echo "=== Versao (409) ==="

# conferir_versao NOME ROTA PUT PATCH OUTRO_PATCH (corpos sem as chaves, sem a versao)
# o OUTRO_PATCH muda um valor de verdade: sem mudanca o hibernate nao sobe a versao
conferir_versao() {
  local nome="$1" rota="$2" put="$3" patch="$4" outro="$5"
  testar "$nome: PATCH com versao 0 (atual)" 200 PATCH "$rota" "{$patch,\"versao\":0}"
  check_valor "$nome: versao sobe pra 1" "1" "$(valor_de "$BODY" versao)"
  testar "$nome: PATCH com versao 0 (velha) -> 409" 409 PATCH "$rota" "{$patch,\"versao\":0}"
  testar "$nome: PUT com versao 0 (velha) -> 409" 409 PUT "$rota" "{$put,\"versao\":0}"
  testar "$nome: PATCH sem versao (nao confere)" 200 PATCH "$rota" "{$outro}"
  check_valor "$nome: versao sobe pra 2" "2" "$(valor_de "$BODY" versao)"
}

criar "/motoristas" "{\"nome\":\"Versao\",\"cnh\":\"R${SUFIXO: -12}\"}"
conferir_versao "motorista" "/motoristas/$NOVO_ID" "\"nome\":\"Versao 2\",\"cnh\":\"R${SUFIXO: -12}\"" "\"telefone\":\"1199\"" "\"telefone\":\"1188\""

criar "/veiculos" "{\"label\":\"Van\",\"placa\":\"R${SUFIXO: -9}\",\"capacidade\":10}"
conferir_versao "veiculo" "/veiculos/$NOVO_ID" "\"label\":\"Van 2\",\"placa\":\"R${SUFIXO: -9}\",\"capacidade\":11" "\"capacidade\":12" "\"capacidade\":13"

criar "/passageiros" "{\"nome\":\"Versao\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"V1\"}"
conferir_versao "passageiro" "/passageiros/$NOVO_ID" "\"nome\":\"Versao 2\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"V1\"" "\"nacionalidade\":\"Chilena\"" "\"nacionalidade\":\"Uruguaia\""

criar_usuario ATENDENTE
conferir_versao "usuario" "/usuarios/$NOVO_ID" "\"nome\":\"Versao 2\",\"username\":\"$NOVO_USUARIO\",\"role\":\"ATENDENTE\"" "\"nome\":\"Versao 3\"" "\"nome\":\"Versao 4\""

criar "/transfers" "{\"dataTransfer\":\"2030-07-01\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\"}"
TRANSFER=$NOVO_ID
criar "/pontos-coleta" "{\"transferId\":$TRANSFER,\"localColeta\":\"P\",\"latitude\":0,\"longitude\":0}"
conferir_versao "ponto de coleta" "/pontos-coleta/$NOVO_ID" "\"transferId\":$TRANSFER,\"localColeta\":\"P2\",\"latitude\":0,\"longitude\":0" "\"localColeta\":\"P3\"" "\"localColeta\":\"P4\""

criar "/ordens-servico" "{\"dataServico\":\"2030-07-01\"}"
OS=$NOVO_ID
criar "/paradas-os" "{\"osId\":$OS,\"ordemParada\":1,\"localParada\":\"A\"}"
conferir_versao "parada" "/paradas-os/$NOVO_ID" "\"osId\":$OS,\"ordemParada\":1,\"localParada\":\"B\"" "\"localParada\":\"C\"" "\"localParada\":\"D\""

conferir_versao "transfer" "/transfers/$TRANSFER" "\"dataTransfer\":\"2030-07-01\",\"horaTransfer\":\"11:00:00\",\"origem\":\"A\",\"destino\":\"C\"" "\"destino\":\"D\"" "\"destino\":\"E\""
conferir_versao "ordem de servico" "/ordens-servico/$OS" "\"dataServico\":\"2030-07-01\"" "\"status\":\"EM_ANDAMENTO\"" "\"status\":\"FINALIZADA\""

resumo
