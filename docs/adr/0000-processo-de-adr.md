# ADR-0000: Processo de ADR desta branch

- **Status:** Aceito
- **Data:** 2026-09-17

## Contexto

O projeto já tinha decisões importantes de código e infraestrutura tomadas (algumas
antigas, algumas de uma sessão de otimização recente) que só existiam na cabeça de
quem escreveu, ou espalhadas em mensagens de commit e conversas. Isso tem dois
problemas: quem entra no projeto depois não sabe *por que* o código é do jeito que é,
e é fácil "desfazer" uma decisão sem querer, por não saber que ela foi deliberada.

## Decisão

Criar uma branch eterna chamada `documentacao`, com uma pasta `docs/adr/` contendo
um arquivo por decisão relevante — Architecture Decision Records (ADR), um formato
usado por várias empresas justamente pra isso.

Regras do processo:

1. **Numeração sequencial e imutável.** Cada ADR tem um número de 4 dígitos
   (`0001`, `0002`, ...), atribuído em ordem crescente e nunca reaproveitado. É o
   mesmo princípio de uma migration do Flyway (`V1__...sql`, `V2__...sql`): uma vez
   criada, uma versão não é renumerada nem apagada.
2. **ADR não se edita pra mudar de ideia.** Se uma decisão for revista depois, a ADR
   antiga é marcada como `Substituído por ADR-000X` no campo Status, e uma ADR nova
   é criada explicando a mudança. O histórico de "por que mudamos de ideia" é tão
   importante quanto a decisão em si.
3. **Toda ADR segue o mesmo template** (ver seção abaixo), incluindo uma seção
   **"Onde encontrar no código"** — o objetivo não é só justificar uma escolha, é
   também servir de mapa pra quem for estudar o projeto.
4. **Quando escrever uma ADR:** sempre que uma escolha não é óbvia a partir do
   código sozinho — por que essa biblioteca e não outra, por que essa regra de
   negócio existe, por que essa configuração de infra. Não é pra documentar
   trivialidades (não precisa de ADR pra "por que usamos getters e setters").
5. **Escopo:** decisões de código do backend e decisões de infraestrutura/deploy
   que afetam como o projeto roda (containers, banco, variáveis de ambiente, CI).
6. **Merge pra `main`:** o conteúdo acumulado em `documentacao` é integrado na
   `main` periodicamente (hoje, o combinado é por volta do dia 20 de cada mês).
   A branch `documentacao` não é apagada depois do merge — ela continua recebendo
   as próximas ADRs, com a numeração de onde parou.

## Template de uma ADR

```markdown
# ADR-000X: Título curto no infinitivo ou substantivo

- **Status:** Proposto | Aceito | Substituído por ADR-000Y | Descontinuado
- **Data:** AAAA-MM-DD

## Contexto
Qual problema motivou essa decisão? O que acontecia antes (ou aconteceria) sem ela?

## Decisão
O que foi feito, de forma direta.

## Alternativas consideradas
O que mais foi cogitado, e por que foi descartado. Se não houve alternativa real
avaliada, diga isso — é informação também.

## Consequências
O que fica mais fácil, o que fica mais difícil, que trade-off foi aceito.

## Onde encontrar no código
Lista de arquivos/classes principais envolvidos, com caminho relativo ao repo.
```

## Alternativas consideradas

- **Wiki externa (Confluence, Notion):** descartado porque fica fora do fluxo de
  code review e some do controle de versão do próprio código — quem revisa um PR
  não vê a doc relacionada, e a doc não tem histórico de `git blame`.
- **Comentários só no código:** já fazemos isso pra explicar *como* um trecho
  funciona, mas comentário não é o lugar certo pra registrar *por que uma
  alternativa foi descartada* — isso precisa de mais espaço e sobrevive à
  refatoração do trecho comentado.
- **Documentar tudo dentro de cada feature branch, sem uma branch dedicada:**
  descartado porque a doc ficaria fragmentada e sumiria (ou ficaria difícil de
  achar) depois que a feature branch fosse apagada.

## Consequências

- Toda decisão não óbvia passa a ter um lugar certo pra ir — reduz decisão
  "perdida" em mensagem de commit ou conversa.
- Tem custo: exige parar e escrever depois de decidir algo, o que é atrito real
  no dia a dia. Vale a pena só se a disciplina for mantida.
- Numeração imutável significa que, se uma ADR for descartada rapidamente, o
  número dela "queima" mesmo assim — é intencional, não é bug do processo.

## Onde encontrar no código

Não se aplica — esta ADR descreve o processo, não uma decisão de código.
