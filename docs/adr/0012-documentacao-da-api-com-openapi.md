# ADR-0012: Documentação da API com OpenAPI / Swagger anotado

- **Status:** Aceito
- **Data:** 2026-09-18

## Contexto

A API já expunha o Swagger UI (`springdoc-openapi`), mas sem nenhuma anotação: os endpoints apareciam com
o nome do método Java, sem descrição, sem exemplos de corpo e sem as respostas de erro. Quem abria
`/swagger-ui.html` conseguia ver as rotas, mas não sabia:

- que precisava fazer login e como colocar o token;
- o que cada endpoint faz e em que casos devolve 400, 404, 409 ou 403;
- que valores enviar (o botão *Try it out* vinha com `"string"` em todos os campos).

Como o Swagger é a "vitrine" da API para quem consome (front, avaliadores, outro time), essa lacuna
transformava um recurso pronto em um recurso pouco útil.

## Decisão

Documentar a API **por anotações no próprio código** (abordagem *code-first*), em quatro níveis:

1. **Grupo por recurso.** Todo controller tem `@Tag(name, description)`: 11 grupos (Autenticação, Usuários,
   Motoristas, Veículos, Passageiros, Transfers, Pontos de coleta, Ordens de serviço, Paradas de OS,
   Cotação, Auditoria). A descrição do grupo carrega regras do domínio (ex.: "o documento é cifrado no banco").
2. **Operação.** Todo método mapeado tem `@Operation(summary)` e `@ApiResponses` com os status que
   realmente devolve (por verbo: `201/400/404` no POST, `204/404/409` no DELETE...). Onde há regra não
   óbvia, há `description` (ex.: conversão de câmbio no `POST /transfers`; MOTORISTA só altera
   `statusParada`; `PUT` sem `passageiroIds` mantém os atuais).
3. **Exemplos de corpo.** Os DTOs de entrada têm `@Schema(example = "...")` com dados verossímeis
   (`Carlos Andrade`, `ABC1D23`, `USD`...), então o *Try it out* já vem preenchido e funcional.
4. **Respostas transversais uma vez só.** Um `OpenApiCustomizer` (`OpenApiConfig.respostasDeSeguranca`)
   adiciona `401` ("Token ausente, inválido ou expirado") e `403` ("Perfil sem permissão") a **todas** as
   operações, exceto o login. Assim não se repete essa anotação em 60 métodos.

Além disso, a descrição geral da API (`OpenApiConfig`) explica o fluxo de uso: *"faça POST /api/auth/login,
copie o token, clique em Authorize e cole"*, e resume os quatro perfis.

O Swagger continua **habilitado apenas no profile `dev`** (ADR-0009): em produção a documentação
interativa não é exposta.

Resultado medido no `/v3/api-docs`: **62 operações, 11 grupos, 0 operações sem resumo, 62 com `401`
documentado.**

## Alternativas consideradas

- **Contrato primeiro (*contract-first*): escrever o `openapi.yaml` à mão e gerar os controllers.**
  É a melhor prática quando há várias equipes consumindo antes de a API existir. Aqui a API e o front
  evoluem juntos e o código já existia; manter um YAML paralelo duplicaria a fonte da verdade e
  desatualizaria a cada mudança. Anotações ficam ao lado do código que descrevem.
- **Não anotar e confiar nos nomes dos métodos.** É o estado anterior; descartado pelo motivo do Contexto.
- **`@ApiResponse` com corpo de erro (`content = @Content(schema = ErroResponseDTO)`) em cada método.**
  Mais completo, porém verboso; o formato de erro é o mesmo em toda a API e está descrito em um lugar só
  (`GlobalExceptionHandler` e a seção "Contrato da API" do `README.md`). Foi mantida a descrição textual.
- **Anotações via meta-anotação própria (`@RespostasCrud`).** Reduziria repetição, mas esconderia os
  status atrás de uma abstração; preferiu-se a leitura direta em cada método.

## Consequências

- **Mais ruído visual nos controllers.** Cada método ganhou 2 linhas de anotação. É o custo do
  *code-first*, mitigado por manter o texto curto e por os controllers continuarem sem regra de negócio.
- **Documentação pode divergir do comportamento** se alguém mudar um status de resposta sem atualizar a
  anotação. Mitigação parcial: os testes de web (`ValidacaoWebTest`, `AutorizacaoWebTest`) fixam os status
  reais; ao mudar um, o revisor vê os dois lados no mesmo PR.
- **Quem cria um endpoint novo deve anotá-lo** (`@Operation` + `@ApiResponses`) e dar exemplos ao DTO. O
  checklist está em `docs/mapa-do-codigo.md`.
- Os exemplos dos DTOs são **dados de exemplo**, não valores padrão: não afetam a validação nem o
  comportamento da API.

## Onde encontrar no código

- Configuração geral, esquema Bearer e respostas 401/403 globais: `config/OpenApiConfig.java`
- `@Tag` e `@Operation`: todos os `controller/*Controller.java`
- Exemplos: `@Schema(example = ...)` nos `dto/*RequestDTO.java`
- Habilitação por profile: `application.yml` (`springdoc.*` e o bloco `dev`)
- Como abrir: `http://localhost:8080/swagger-ui.html` (profile `dev`)
