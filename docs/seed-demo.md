# Seed de demonstração (do zero)

Popula a API com dados mock (usuários, motoristas, veículos, passageiros, transfers, OS e pontos de coleta) pela própria API.

## Pré-requisitos

- Stack no ar: `docker compose up -d postgres app front` (API em `http://localhost:9090`).
- `bash`, `curl` e `jq` no PATH (no Windows, use o Git Bash).

## Rodar

```bash
AJT_URL=http://localhost:9090 AJT_PASS=admin123 bash scripts/seed-demo.sh
```

- `AJT_PASS` é a senha do `admin`. Padrão do seed: `admin123`.
- Senha dos usuários de demo: `AJT_DEMO_PASS` (padrão `demo123456`).
- Ao final, o script mostra as linhas `[FAIL]` para cada chamada recusada.

## Atenção

- **Não apaga nada.** Rodar duas vezes no mesmo banco gera `[FAIL]` (username, CNH, placa e documento duplicados).
- Para começar do zero, apague o volume do banco e suba de novo:

```bash
docker compose down -v
docker compose up -d postgres app front
```

Depois rode o seed uma vez.

## Usuário de teste vazio (já criado)

- Login: `teste.vazio`
- Senha: `teste12345`
- Perfil: `ATENDENTE`, sem outros dados. A API exige `nome` e `role` no cadastro, por isso o usuário tem nome `Teste Vazio`.
