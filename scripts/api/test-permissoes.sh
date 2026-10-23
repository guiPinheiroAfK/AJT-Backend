#!/usr/bin/env bash
# o que testa: a matriz de permissoes do javadoc do SecurityConfig, logando de verdade com
# um usuario de cada perfil (ADMIN, GERENTE, ATENDENTE, MOTORISTA) nas rotas principais,
# mais o 401 sem token.
# como rodar: bash scripts/api/test-permissoes.sh (api de pe; ver common.sh).
# por que existe: o teste web prova a matriz com usuario simulado; aqui o token vem do login
# real e o perfil vem do banco.
#
# truque pra nao criar dado: quem tem permissao recebe 400 (corpo vazio) ou 404 (id que nao
# existe); quem nao tem recebe 403 antes de chegar na validacao.
source "$(dirname "$0")/common.sh"
echo "=== Permissoes por perfil ==="

NAO_EXISTE=999999999

# um token por perfil em TOKEN_ADMIN, TOKEN_GERENTE... (sem array associativo, roda em bash 3)
for perfil in ADMIN GERENTE ATENDENTE MOTORISTA; do
  criar_usuario "$perfil"
  printf -v "TOKEN_$perfil" '%s' "$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")"
done

# como PERFIL STATUS METODO ROTA [CORPO]
como() {
  local perfil="$1" esperado="$2" metodo="$3" rota="$4" corpo="${5:-}"
  local var="TOKEN_$perfil"
  testar "$perfil $metodo $rota" "$esperado" "$metodo" "$rota" "$corpo" "${!var}"
}

testar "sem token GET /transfers -> 401" 401 GET "/transfers" "" -

# usuarios: so ADMIN
como ADMIN     200 GET    "/usuarios"
como GERENTE   403 GET    "/usuarios"
como ATENDENTE 403 GET    "/usuarios"
como MOTORISTA 403 GET    "/usuarios"
como ADMIN     404 DELETE "/usuarios/$NAO_EXISTE"
como GERENTE   403 DELETE "/usuarios/$NAO_EXISTE"

# auditoria: leitura so ADMIN e GERENTE
como ADMIN     200 GET "/auditoria"
como GERENTE   200 GET "/auditoria"
como ATENDENTE 403 GET "/auditoria"
como MOTORISTA 403 GET "/auditoria"

# motoristas, veiculos, ordens-servico: leitura todos, escrita ADMIN e GERENTE
como MOTORISTA 200 GET   "/motoristas"
como ATENDENTE 200 GET   "/veiculos"
como GERENTE   400 POST  "/ordens-servico" "{}"
como ATENDENTE 403 POST  "/ordens-servico" "{}"
como MOTORISTA 403 PATCH "/veiculos/$NAO_EXISTE" '{"marca":"Fiat"}'
como ATENDENTE 403 PUT   "/motoristas/$NAO_EXISTE" '{"nome":"X","cnh":"1"}'
como GERENTE   404 PUT   "/motoristas/$NAO_EXISTE" '{"nome":"X","cnh":"1"}'

# transfers e pontos-coleta: leitura todos, escrita ADMIN, GERENTE e ATENDENTE, DELETE ADMIN e GERENTE
como MOTORISTA 200 GET    "/transfers"
como ATENDENTE 400 POST   "/transfers" "{}"
como MOTORISTA 403 POST   "/transfers" "{}"
como ATENDENTE 400 POST   "/pontos-coleta" "{}"
como MOTORISTA 403 POST   "/pontos-coleta" "{}"
como GERENTE   404 DELETE "/transfers/$NAO_EXISTE"
como ATENDENTE 403 DELETE "/transfers/$NAO_EXISTE"

# passageiros: MOTORISTA nao le o cadastro (LGPD)
como ATENDENTE 200 GET   "/passageiros"
como MOTORISTA 403 GET   "/passageiros"
como MOTORISTA 403 GET   "/passageiros/$NAO_EXISTE"
como MOTORISTA 403 PATCH "/passageiros/$NAO_EXISTE" '{"nome":"X"}'
como ATENDENTE 404 GET   "/passageiros/$NAO_EXISTE"

# paradas-os: MOTORISTA so PATCH (so o status, regra do service), ATENDENTE nem isso
como MOTORISTA 404 PATCH  "/paradas-os/$NAO_EXISTE" '{"statusParada":"CONCLUIDA"}'
como ATENDENTE 403 PATCH  "/paradas-os/$NAO_EXISTE" '{"statusParada":"CONCLUIDA"}'
como MOTORISTA 403 POST   "/paradas-os" "{}"
como MOTORISTA 403 DELETE "/paradas-os/$NAO_EXISTE"

# minhas OS do dia: so MOTORISTA
como MOTORISTA 200 GET "/ordens-servico/minhas"
como GERENTE   403 GET "/ordens-servico/minhas"
como ATENDENTE 403 GET "/ordens-servico/minhas"

# painel e auth/me: todos
for perfil in ADMIN GERENTE ATENDENTE MOTORISTA; do
  como "$perfil" 200 GET "/painel/resumo"
  como "$perfil" 200 GET "/auth/me"
done

# o perfil vem do banco, nao do token: rebaixar um gerente vale na hora, sem novo login
criar_usuario GERENTE
TOKEN_REBAIXADO=$(login_como "$NOVO_USUARIO" "$NOVO_SENHA")
testar "gerente POST /ordens-servico antes de ser rebaixado" 400 POST "/ordens-servico" "{}" "$TOKEN_REBAIXADO"
testar "admin rebaixa o gerente pra MOTORISTA" 200 PATCH "/usuarios/$NOVO_ID" '{"role":"MOTORISTA"}'
testar "mesmo token POST /ordens-servico depois de rebaixado -> 403" 403 POST "/ordens-servico" "{}" "$TOKEN_REBAIXADO"

resumo
