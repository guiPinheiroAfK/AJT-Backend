# ADR-0022: Scripts de verificação da API organizados em `scripts/`

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

Havia dois conjuntos de scripts bash para conferir a API rodando:

- `scripts/smoke-test.sh` (2026-09-18, ADR-0014): a sequência da demonstração, 70 verificações, usa `jq`;
- `api/src/tests/*.sh` (agosto e setembro de 2026): um script por recurso, com `common.sh` e `run-all.sh`.

Os scripts de `api/src/tests` tinham três problemas:

- a pasta ficava ao lado de `api/src/test` (os testes Java), um `s` de diferença que confundia;
- usavam dados fixos (CNH, placa, username), então **a segunda execução quebrava com dado duplicado**;
- cobriam só o CRUD feliz de 9 recursos, sem filtros, versão, permissões por perfil, HU07, painel,
  auditoria nem cotação, e o `run-all.sh` não dava resumo nem código de saída.

## Decisão

- **Tudo em `scripts/`:** o `smoke-test.sh` continua na raiz de `scripts/`, e os scripts por recurso vão para
  `scripts/api/` (`common.sh`, `run-all.sh` e um `test-<recurso>.sh` por assunto, 16 ao todo).
- **`common.sh` em comum:**
  - mesmas variáveis do smoke: `AJT_URL` (padrão `http://localhost:8080`), `AJT_USER` e `AJT_PASS`;
  - helpers `req`, `testar`, `check_status`, `check_valor`, `valor_de`, `id_de`, `login_como`,
    `criar_usuario`, `criar` e `apagar_no_fim`;
  - contadores `PASS`/`FAIL` e `resumo`, que sai com código 1 se houve falha.
- **Pode rodar duas vezes seguidas:** cada execução usa um sufixo único (`$(date +%s)$RANDOM`) nos campos
  únicos, e tudo o que o script cria é apagado no fim por um `trap limpar EXIT`, mesmo se ele parar no meio.
- **Só `bash` e `curl` (+ `grep`/`sed`)** nos scripts por recurso, sem `jq`, e compatíveis com o Git Bash do
  Windows (sem `date -d`, sem `sed -i` sem sufixo, sem array associativo).
- **Usuários descartáveis** para o que pode trancar uma conta (troca de senha e bloqueio por tentativas),
  nunca o admin.
- **`run-all.sh` descobre os scripts pelo nome** (`test-*.sh`), roda o `test-auth.sh` primeiro (sem login
  nada funciona), mostra o resumo de cada um e o total, e sai com 1 se qualquer um falhar.
- **`.gitattributes` com `*.sh text eol=lf`**: no Windows o CRLF quebra o shebang e o `source`.

Diferenças entre o que se esperava e o que a API faz, registradas nos próprios scripts:

- CNH, placa ou username duplicado devolve **400** (`RegistroDuplicadoException`), não 409. O 409 aparece ao
  excluir um registro em uso (FK) e na versão velha (ADR-0016).
- `GET /api/paradas-os/ordem-servico/{id}` de uma OS inexistente devolve **200 com lista vazia** para ADMIN e
  GERENTE. Só o MOTORISTA recebe 404 (ADR-0018).

## Alternativas consideradas

- **Apagar `api/src/tests` e ficar só com o smoke test** (o que já tinha sido feito com a pasta `tests/`
  antiga em `55d4f1f`): o smoke cobre a demonstração, mas não os caminhos tristes por recurso.
- **Newman com a coleção do Postman:** já descartado na ADR-0014 por exigir Node.
- **`jq` também nos scripts por recurso:** descartado para não exigir mais uma instalação no Windows. O
  smoke continua usando `jq`.

## Consequências

- Contra a API de pé, 16 scripts e 411 verificações rodam em sequência, duas vezes seguidas, sem sobrar dado.
- Os scripts criam e apagam dados e usam o login do ADMIN: **não devem ser apontados para produção**.
- O `test-cotacao.sh` depende da API de câmbio externa responder (502 se ela estiver fora).
- O `smoke-test.sh` ainda termina com código 0 mesmo quando alguma verificação falha.

## Onde encontrar no código

- `scripts/api/common.sh`, `scripts/api/run-all.sh`, `scripts/api/test-*.sh`
- `scripts/smoke-test.sh`
- `.gitattributes` (raiz do repositório)
- Como rodar: seção *Testes* do `README.md`
