# Documentação do AJT-Backend

Esta pasta é o registro permanente das decisões de arquitetura, código e infraestrutura
do projeto. A ideia é simples: quando alguém (você daqui a 6 meses, ou outra pessoa do
time) olhar pra um trecho de código e perguntar "por que isso foi feito assim?", a
resposta está aqui — não precisa adivinhar, nem perguntar, nem quebrar a decisão sem
saber que ela existia.

## Onde estamos

Esta é a branch `documentacao`, uma **branch eterna**: ela nunca é apagada e não segue
o ciclo normal de feature branch (criar → revisar → mergear → apagar). O trabalho de
documentar acontece direto aqui. Periodicamente — hoje o combinado é sempre por volta
do dia 20 de cada mês — o conteúdo acumulado aqui é mergeado na `main`, e a branch
`documentacao` continua viva pra receber as próximas ADRs.

## Como funciona (resumo — o processo completo está na [ADR-0000](adr/0000-processo-de-adr.md))

- Cada decisão relevante vira um arquivo em `docs/adr/`, numerado sequencialmente:
  `0001-titulo-curto.md`, `0002-titulo-curto.md`, e assim por diante.
- A numeração é **imutável**: um número usado nunca é reaproveitado, nunca é
  reordenado. Se uma decisão muda de ideia depois, a ADR antiga não é editada pra
  "sumir" — ela é marcada como **substituída** e uma ADR nova é criada. É o mesmo
  princípio de uma migration do Flyway: uma vez aplicada, não se edita o passado.
- Todo ADR segue o mesmo template (contexto, decisão, alternativas consideradas,
  consequências, onde encontrar no código) — ver [ADR-0000](adr/0000-processo-de-adr.md).

## Índice de decisões

| ADR | Título | Status |
|---|---|---|
| [0000](adr/0000-processo-de-adr.md) | Processo de ADR desta branch | Aceito |
| [0001](adr/0001-enums-em-vez-de-strings-livres.md) | Enums em vez de strings livres para status e perfil | Aceito |
| [0002](adr/0002-matriz-de-permissoes-por-perfil.md) | Matriz de permissões por perfil (RBAC via Spring Security) | Aceito |
| [0003](adr/0003-criptografia-documento-passageiro.md) | Criptografia do documento do passageiro (AES-256-GCM) | Aceito |
| [0004](adr/0004-seguranca-de-login-e-sessao.md) | Bloqueio de login, invalidação de token e troca de senha obrigatória | Aceito |
| [0005](adr/0005-paginacao-padrao-nas-listagens.md) | Paginação padrão em todas as listagens | Aceito |
| [0006](adr/0006-cache-e-timeout-da-cotacao-de-cambio.md) | Cache e isolamento transacional da cotação de câmbio | Aceito |
| [0007](adr/0007-correcao-do-n-mais-1-nas-paradas.md) | Correção do N+1 nas paradas de OS | Aceito |
| [0008](adr/0008-testcontainers-singleton-e-versao-fixada.md) | Testcontainers: container singleton e versão fixada (deploy/CI) | Aceito |
| [0009](adr/0009-infra-perfis-container-e-rede.md) | Infra: perfis dev/prod, container sem root, Postgres restrito (deploy) | Aceito |

## Para quem está estudando o projeto agora

Se você chegou aqui sem contexto nenhum, a ordem de leitura sugerida é:

1. [ADR-0001](adr/0001-enums-em-vez-de-strings-livres.md) e [ADR-0002](adr/0002-matriz-de-permissoes-por-perfil.md) —
   entendem o modelo de dados e quem pode fazer o quê.
2. [ADR-0004](adr/0004-seguranca-de-login-e-sessao.md) — como o login e a sessão funcionam de ponta a ponta.
3. [ADR-0003](adr/0003-criptografia-documento-passageiro.md) — por que um campo específico é tratado diferente dos outros.
4. [ADR-0005](adr/0005-paginacao-padrao-nas-listagens.md), [ADR-0006](adr/0006-cache-e-timeout-da-cotacao-de-cambio.md) e
   [ADR-0007](adr/0007-correcao-do-n-mais-1-nas-paradas.md) — decisões de performance.
5. [ADR-0008](adr/0008-testcontainers-singleton-e-versao-fixada.md) e
   [ADR-0009](adr/0009-infra-perfis-container-e-rede.md) — como o projeto é testado e roda em produção.

Cada ADR tem uma seção **"Onde encontrar no código"** com os arquivos exatos — não é só
teoria, é o mapa de onde cavar.
