# ADR-0015: CSRF desligado na API stateless com token Bearer

- **Status:** Aceito
- **Data:** 2026-10-22

## Contexto

A análise do SonarQube marcou uma vulnerabilidade de impacto alto (regra `java:S4502`, "CSRF protections
should not be disabled") no `SecurityConfig`, por causa do `csrf.disable()`. Era a única vulnerabilidade do
projeto, e ela sozinha derrubava a nota de segurança para D e reprovava o requisito de "zero
vulnerabilidades" do Quality Gate.

CSRF (Cross-Site Request Forgery) é um site de terceiros fazer o navegador da vítima enviar uma requisição
para a nossa API **usando uma credencial que o navegador anexa sozinho** — cookie de sessão ou login
*basic* guardado pelo navegador. A proteção do Spring (token CSRF) existe para separar a requisição que a
nossa tela montou da requisição forjada.

Nesta API essa credencial automática não existe:

- a sessão é `STATELESS` (o Spring Security não cria `HttpSession`, logo não há `JSESSIONID`);
- o login devolve o JWT **no corpo da resposta**, não em cookie;
- toda chamada autenticada leva o token no header `Authorization: Bearer ...`, que o navegador **não**
  acrescenta sozinho numa requisição disparada por outra origem;
- o CORS está com `allowCredentials(false)` e só libera as origens de `AJT_CORS_ORIGINS`.

## Decisão

Manter o CSRF desligado e registrar a decisão no próprio código, em vez de esconder o aviso:

- o `csrf.disable()` saiu da cadeia principal e foi para um método só dele, `SecurityConfig.semCsrf`, com um
  javadoc explicando o motivo;
- a supressão é **só da regra `java:S4502`** e **só nesse método** (`@SuppressWarnings("java:S4502")`), e
  não um `// NOSONAR` (que cala todas as regras da linha) nem uma marcação manual no painel do SonarQube
  (que se perde ao recriar o projeto ou ao rodar em outra máquina);
- as premissas que tornam o CSRF desnecessário viraram teste em `AutenticacaoIntegrationTest`:
  - `loginNaoAbreSessaoNemDevolveCookie`: o login não cria sessão nem manda `Set-Cookie`;
  - `cookieSemOTokenNaoAutorizaEscrita`: uma escrita que chega só com cookie, sem o header
    `Authorization`, recebe 401.

Se alguém passar a usar sessão ou cookie para autenticar, esses testes quebram e obrigam a rever esta ADR.

## Alternativas consideradas

- **Ligar o CSRF do Spring** (`CookieCsrfTokenRepository` + o suporte a XSRF do Angular): todo cliente
  (front, Postman, a suíte de testes) teria que primeiro buscar um token CSRF e reenviá-lo em cada POST,
  PUT, PATCH e DELETE, inclusive no login. Muda o contrato da API e o front sem proteger nada, porque não
  há credencial automática para um atacante reaproveitar. Descartado.
- **`ignoringRequestMatchers("/api/**")` ou `requireCsrfProtectionMatcher(...)`**: na prática é desligar
  do mesmo jeito, só que mais escondido. A própria regra S4502 marca esses dois métodos também.
- **Marcar a issue como "Accepted"/"False positive" no painel do SonarQube**: válido, mas a justificativa
  fica fora do repositório e some numa instalação nova do SonarQube (cada pessoa do time roda o seu
  localmente). Fica como alternativa caso a avaliação não aceite supressão no código.

## Consequências

- O Quality Gate passa a mostrar zero vulnerabilidades, e a nota de segurança volta para A.
- A decisão fica visível para quem lê o `SecurityConfig` e é verificada pelos testes, não só afirmada.
- **Trade-off aceito:** se um dia a autenticação passar a usar cookie (por exemplo, guardar o JWT em cookie
  `HttpOnly`), o CSRF **precisa** ser ligado. Os dois testes acima existem para que essa mudança não passe
  despercebida.

## Onde encontrar no código

- `api/src/main/java/com/AJTBackend/config/SecurityConfig.java` — método `semCsrf` e a cadeia `securityFilterChain`
- `api/src/test/java/com/AJTBackend/integracao/AutenticacaoIntegrationTest.java` — testes
  `loginNaoAbreSessaoNemDevolveCookie` e `cookieSemOTokenNaoAutorizaEscrita`
- [ADR-0004](0004-seguranca-de-login-e-sessao.md) — como o token é emitido, validado e invalidado

## Atualizações posteriores

Registro append-only: a decisão original acima continua valendo; abaixo, o que a estendeu depois.

- **2026-10-22 ([ADR-0021](0021-estrategia-de-testes-v2-slices-e-bdd.md)):** os dois testes que verificam as
  premissas foram renomeados no padrão `deve…_quando…`:
  - `loginNaoAbreSessaoNemDevolveCookie` virou `naoDeveAbrirSessaoNemDevolverCookie_quandoLoginDaCerto`;
  - `cookieSemOTokenNaoAutorizaEscrita` virou `deveRetornar401_quandoEscritaChegaSoComCookieSemToken`.

  O comportamento verificado é o mesmo.
