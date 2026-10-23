#!/usr/bin/env bash
# o que testa: CRUD de /api/usuarios (POST, GET lista, GET id, GET buscar por username,
# PUT, PATCH, DELETE e GET depois do delete) e os erros: 400 corpo invalido, 400 username
# duplicado, 400 ao tentar se excluir, 404 id e username inexistentes.
# como rodar: bash scripts/api/test-usuarios.sh (api de pe; ver common.sh). so ADMIN usa esta rota.
# por que existe: e quem controla o acesso a todo o resto do sistema.
source "$(dirname "$0")/common.sh"
echo "=== Usuarios ==="

USERNAME="maria.$SUFIXO"

testar "POST /usuarios" 201 POST "/usuarios" "{\"nome\":\"Maria Souza\",\"username\":\"$USERNAME\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\"}"
ID=$(id_de "$BODY")
apagar_no_fim "/usuarios/$ID"
check_valor "usuario novo precisa trocar a senha" "true" "$(valor_de "$BODY" trocarSenha)"

testar "GET /usuarios" 200 GET "/usuarios"
testar "GET /usuarios/$ID" 200 GET "/usuarios/$ID"
testar "GET /usuarios/buscar?username=" 200 GET "/usuarios/buscar?username=$USERNAME"
check_valor "busca por username acha o usuario criado" "$ID" "$(id_de "$BODY")"

testar "PUT /usuarios/$ID" 200 PUT "/usuarios/$ID" "{\"nome\":\"Maria Souza Lima\",\"username\":\"$USERNAME\",\"role\":\"GERENTE\"}"
check_valor "PUT troca o perfil" "GERENTE" "$(valor_de "$BODY" role)"
testar "PATCH /usuarios/$ID (desativar)" 200 PATCH "/usuarios/$ID" '{"ativo":false}'
check_valor "PATCH desativa" "false" "$(valor_de "$BODY" ativo)"

# caminhos tristes
testar "POST /usuarios (username com espaco) -> 400" 400 POST "/usuarios" '{"nome":"X","username":"a b","senha":"senha12345","role":"ATENDENTE"}'
testar "POST /usuarios (senha curta) -> 400" 400 POST "/usuarios" "{\"nome\":\"X\",\"username\":\"x.$SUFIXO\",\"senha\":\"curta\",\"role\":\"ATENDENTE\"}"
testar "POST /usuarios (perfil fora do enum) -> 400" 400 POST "/usuarios" "{\"nome\":\"X\",\"username\":\"y.$SUFIXO\",\"senha\":\"senha12345\",\"role\":\"SUPERUSER\"}"
testar "POST /usuarios (sem senha) -> 400" 400 POST "/usuarios" "{\"nome\":\"X\",\"username\":\"z.$SUFIXO\",\"role\":\"ATENDENTE\"}"
testar "POST /usuarios (username duplicado) -> 400" 400 POST "/usuarios" "{\"nome\":\"Outra\",\"username\":\"$USERNAME\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\"}"
testar "GET /usuarios/999999999 -> 404" 404 GET "/usuarios/999999999"
testar "PUT /usuarios/999999999 -> 404" 404 PUT "/usuarios/999999999" "{\"nome\":\"X\",\"username\":\"w.$SUFIXO\",\"role\":\"ATENDENTE\"}"
testar "PATCH /usuarios/999999999 -> 404" 404 PATCH "/usuarios/999999999" '{"nome":"X"}'
testar "DELETE /usuarios/999999999 -> 404" 404 DELETE "/usuarios/999999999"
testar "GET /usuarios/buscar?username=inexistente -> 404" 404 GET "/usuarios/buscar?username=ninguem-$SUFIXO"
testar "GET /usuarios/buscar (sem username) -> 400" 400 GET "/usuarios/buscar"

# o admin nao pode excluir a si mesmo
req GET "/auth/me"
EU=$(id_de "$BODY")
testar "DELETE /usuarios/$EU (o proprio admin) -> 400" 400 DELETE "/usuarios/$EU"

testar "DELETE /usuarios/$ID" 204 DELETE "/usuarios/$ID"
testar "GET /usuarios/$ID (depois do delete) -> 404" 404 GET "/usuarios/$ID"

resumo
