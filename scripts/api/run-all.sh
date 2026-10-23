#!/usr/bin/env bash
# roda todos os scripts scripts/api/test-*.sh contra a api de pe, mostra o resumo de cada
# um e o total, e sai com 1 se qualquer um falhar.
#
# como usar:
#   bash scripts/api/run-all.sh
#   AJT_URL=http://localhost:9090 AJT_USER=admin AJT_PASS=minhaSenha bash scripts/api/run-all.sh
#
# os scripts sao descobertos pelo nome (test-*.sh). o test-auth.sh roda primeiro porque, se
# o login nao funcionar, nada mais funciona. os outros sao independentes: cada um cria e
# apaga os proprios dados, entao a ordem alfabetica serve.
cd "$(dirname "$0")" || exit 1

SCRIPTS=(test-auth.sh)
for script in test-*.sh; do
  [ "$script" == "test-auth.sh" ] || SCRIPTS+=("$script")
done

TOTAL_OK=0
TOTAL_FAIL=0
FALHARAM=()
LINHAS=()

for script in "${SCRIPTS[@]}"; do
  saida=$(bash "$script" 2>&1)
  codigo=$?
  echo "$saida"
  linha=$(echo "$saida" | grep "^RESUMO " | tail -1)
  ok=$(echo "$linha" | sed -n 's/.*OK=\([0-9]*\).*/\1/p')
  fail=$(echo "$linha" | sed -n 's/.*FAIL=\([0-9]*\).*/\1/p')
  TOTAL_OK=$((TOTAL_OK + ${ok:-0}))
  TOTAL_FAIL=$((TOTAL_FAIL + ${fail:-0}))
  if [ "$codigo" -ne 0 ]; then
    FALHARAM+=("$script")
    # script que caiu antes do resumo (ex: login) conta como uma falha
    [ -z "$linha" ] && TOTAL_FAIL=$((TOTAL_FAIL + 1))
  fi
  LINHAS+=("$(printf '%-30s OK=%-4s FAIL=%s' "$script" "${ok:-0}" "${fail:-?}")")
done

echo "=================== RESUMO GERAL ==================="
printf '%s\n' "${LINHAS[@]}"
echo "TOTAL: OK=$TOTAL_OK FAIL=$TOTAL_FAIL (scripts: ${#SCRIPTS[@]})"
if [ "${#FALHARAM[@]}" -gt 0 ]; then
  echo "scripts com falha: ${FALHARAM[*]}"
  exit 1
fi
exit 0
