#!/usr/bin/env bash
# o que testa: os filtros das listagens (RF015) somados: status, periodo (dataInicio/dataFim)
# e busca por texto em transfers, ordens de servico, passageiros e motoristas; % e _
# digitados tratados como texto; periodo invertido e ordenacao por campo inexistente = 400.
# como rodar: bash scripts/api/test-filtros.sh (api de pe; ver common.sh).
# por que existe: filtro errado nao da erro, so devolve a lista errada. cada consulta aqui
# usa o sufixo desta execucao no texto, entao conta so o que o proprio script criou.
source "$(dirname "$0")/common.sh"
echo "=== Filtros ==="

T="F$SUFIXO"

transfer() {
  criar "/transfers" "{\"dataTransfer\":\"$1\",\"horaTransfer\":\"10:00:00\",\"origem\":\"$2\",\"destino\":\"Hotel\"$3}"
}

transfer "2030-06-01" "Rodoviaria $T" ""
transfer "2030-06-02" "Aeroporto $T" ""
transfer "2030-06-03" "Aeroporto $T" ",\"status\":\"CONFIRMADO\""
transfer "2030-06-02" "Terminal 100% $T" ""
transfer "2030-06-02" "Terminal 1000 $T" ""
transfer "2030-06-02" "Ponto_A $T" ""
transfer "2030-06-02" "PontoXA $T" ""

# transfers
testar "GET /transfers?busca= (so o texto)" 200 GET "/transfers?busca=$T"
check_valor "busca acha os 7 transfers desta execucao" "7" "$(valor_de "$BODY" totalElementos)"
testar "GET /transfers?busca=&dataInicio=&dataFim=" 200 GET "/transfers?busca=$T&dataInicio=2030-06-02&dataFim=2030-06-03"
check_valor "periodo de 02 a 03 deixa 6" "6" "$(valor_de "$BODY" totalElementos)"
testar "GET /transfers?busca=aeroporto&status=CONFIRMADO&periodo" 200 GET "/transfers?busca=aeroporto%20$T&status=CONFIRMADO&dataInicio=2030-06-01&dataFim=2030-06-30"
check_valor "busca + status + periodo somados acham 1" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /transfers?busca=AEROPORTO (sem diferenciar maiusculas)" 200 GET "/transfers?busca=AEROPORTO%20$T"
check_valor "maiusculas acham os 2 do aeroporto" "2" "$(valor_de "$BODY" totalElementos)"
testar "GET /transfers?busca=100% (porcento como texto)" 200 GET "/transfers?busca=100%25%20$T"
check_valor "% digitado nao vira curinga" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /transfers?busca=o_A (underline como texto)" 200 GET "/transfers?busca=o_A%20$T"
check_valor "_ digitado nao vira curinga" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /transfers?semOs=true&busca=" 200 GET "/transfers?semOs=true&busca=$T"
check_valor "os 7 estao sem OS" "7" "$(valor_de "$BODY" totalElementos)"
testar "GET /transfers (periodo invertido) -> 400" 400 GET "/transfers?dataInicio=2030-06-05&dataFim=2030-06-01"
testar "GET /transfers (data em formato errado) -> 400" 400 GET "/transfers?dataFim=31/10/2030"
testar "GET /transfers?sort=campoQueNaoExiste -> 400" 400 GET "/transfers?sort=campoQueNaoExiste"
testar "GET /transfers?sort=dataTransfer,desc" 200 GET "/transfers?busca=$T&sort=dataTransfer,desc"
check_valor "ordenado pela data desc, o primeiro e do dia 03" "2030-06-03" "$(valor_de "$BODY" dataTransfer)"

# ordens de servico
criar "/motoristas" "{\"nome\":\"Filtro $T\",\"cnh\":\"C${SUFIXO: -12}\"}"
MOTORISTA=$NOVO_ID
criar "/ordens-servico" "{\"dataServico\":\"2030-06-01\",\"motoristaId\":$MOTORISTA}"
criar "/ordens-servico" "{\"dataServico\":\"2030-06-02\",\"motoristaId\":$MOTORISTA,\"status\":\"EM_ANDAMENTO\"}"
testar "GET /ordens-servico?motoristaId=" 200 GET "/ordens-servico?motoristaId=$MOTORISTA"
check_valor "o motorista tem 2 OS" "2" "$(valor_de "$BODY" totalElementos)"
testar "GET /ordens-servico?motoristaId=&dataInicio=" 200 GET "/ordens-servico?motoristaId=$MOTORISTA&dataInicio=2030-06-02"
check_valor "a partir do dia 02 sobra 1" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /ordens-servico?motoristaId=&status=ABERTA" 200 GET "/ordens-servico?motoristaId=$MOTORISTA&status=ABERTA"
check_valor "so 1 esta ABERTA" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /ordens-servico (periodo invertido) -> 400" 400 GET "/ordens-servico?dataInicio=2030-06-05&dataFim=2030-06-01"
testar "GET /ordens-servico?motoristaId=um -> 400" 400 GET "/ordens-servico?motoristaId=um"

# passageiros e motoristas
criar "/passageiros" "{\"nome\":\"Ana $T\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X1\",\"nacionalidade\":\"Argentina\"}"
criar "/passageiros" "{\"nome\":\"Bia $T\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X2\",\"nacionalidade\":\"Brasileira\"}"
testar "GET /passageiros?nome= (parte do nome)" 200 GET "/passageiros?nome=$(echo "$T" | tr 'A-Z' 'a-z')"
check_valor "nome em minusculo acha os 2" "2" "$(valor_de "$BODY" totalElementos)"
testar "GET /passageiros?nome=&nacionalidade=" 200 GET "/passageiros?nome=$T&nacionalidade=argen"
check_valor "nome + nacionalidade acham 1" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /motoristas?nome=" 200 GET "/motoristas?nome=$T"
check_valor "filtro de motorista por nome acha 1" "1" "$(valor_de "$BODY" totalElementos)"
testar "GET /motoristas?sort=xpto,asc -> 400" 400 GET "/motoristas?sort=xpto,asc"

resumo
