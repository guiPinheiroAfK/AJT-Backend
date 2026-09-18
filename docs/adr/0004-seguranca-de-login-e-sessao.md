# ADR-0004: Bloqueio de login, invalidação de token e troca de senha obrigatória

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

O login original tinha três problemas de segurança:

1. **Sem limite de tentativas** — nada impedia tentar adivinhar a senha de um
   usuário indefinidamente.
2. **Tempo de resposta revelava usuários existentes** — o hash BCrypt só era
   verificado quando o usuário existia; login com username inexistente respondia
   quase instantaneamente, enquanto username existente + senha errada demorava o
   tempo do BCrypt. Dava pra enumerar usernames válidos só medindo o tempo de
   resposta.
3. **Token continuava válido depois de mudanças no usuário** — o JWT carregava a
   role no próprio token, e o filtro nunca consultava o banco. Desativar um
   usuário, trocar a senha dele ou mudar sua role não tinha efeito nas sessões já
   abertas até o token expirar (até 24h, pelo `AJT_JWT_EXPIRATION` padrão).

## Decisão

- **Bloqueio por tentativas** (`LoginAttemptService`, cache em memória via
  Caffeine): depois de N tentativas erradas (`AJT_LOGIN_MAX_TENTATIVAS`, padrão
  5) do mesmo IP + username, login retorna `429` por um tempo
  (`AJT_LOGIN_BLOQUEIO_MINUTOS`, padrão 15). Sucesso zera o contador.
- **Hash falso pra igualar o tempo de resposta**: quando o username não existe,
  o `AuthService` ainda roda um `passwordEncoder.matches()` contra um hash
  gerado aleatoriamente no boot da aplicação, em vez de pular essa etapa. Assim
  a resposta demora aproximadamente o mesmo tempo, exista ou não o username.
- **Perfil e status vêm do banco, não do token**: o `JwtAuthenticationFilter`
  usa o JWT só pra saber *quem* é (username) e *se* o token é válido/não
  expirou. A role usada pra autorização, e a checagem de "usuário está ativo",
  vêm de uma consulta ao `UsuarioRepository` a cada requisição. Isso significa
  que desativar ou rebaixar um usuário tem efeito imediato, sem esperar o token
  expirar.
- **Invalidação por troca de senha**: a entidade `Usuario` ganhou o campo
  `senhaAlteradaEm`. O filtro compara o `issuedAt` (data de emissão) do token
  com esse campo — token emitido antes da última troca de senha é rejeitado.
  Isso cobre tanto o usuário trocando a própria senha quanto um admin
  redefinindo a senha de outra pessoa.
- **Senha temporária obrigatória**: campo `trocarSenha` na entidade `Usuario`.
  É `true` quando: o usuário `admin` do seed ainda está com a senha padrão
  (`V6__Seguranca_E_Indices.sql` marca isso na migration), ou quando um admin
  cria/redefine a senha de outro usuário. O login devolve esse campo, e o front
  deve forçar a tela de troca de senha antes de liberar o resto do app.

## Alternativas consideradas

- **Blacklist de tokens revogados (Redis ou tabela no banco):** resolveria a
  invalidação de forma mais tradicional, mas exige infraestrutura extra (Redis)
  ou uma tabela que cresce e precisa de limpeza. A comparação por timestamp
  (`senhaAlteradaEm` vs `issuedAt`) resolve o caso real (senha trocada, perfil
  mudou) sem estado adicional — o "estado" já é a própria entidade `Usuario`,
  que já seria consultada de qualquer forma.
- **JWT de vida curta + refresh token:** reduziria a janela de token
  desatualizado sem precisar consultar o banco a cada requisição, mas adiciona
  complexidade (endpoint de refresh, rotação de refresh token) que não foi
  considerada necessária ainda pro tamanho atual do projeto. Fica como
  candidato natural se a consulta ao banco a cada requisição virar gargalo.
- **Bloqueio de login só por username (sem IP):** descartado por permitir que
  um atacante bloqueie o login de qualquer usuário legítimo só errando a senha
  dele propositalmente (negação de serviço direcionada). Combinar IP + username
  reduz esse risco.

## Consequências

- Toda requisição autenticada agora faz uma consulta a mais no banco (buscar o
  `Usuario` pelo username) — aceitável dado que já é uma tabela pequena e
  indexada por `username` (índice único), mas é uma consulta a mais por request
  que não existia antes.
- Login errado repetidas vezes agora é visivelmente mais lento (hash BCrypt
  sempre roda) — impacto de UX mínimo, aceito em troca de fechar o timing
  attack.
- Sem Redis/estado compartilhado, o bloqueio de tentativas (`LoginAttemptService`)
  é por instância da aplicação — rodando mais de uma instância atrás de um load
  balancer, o limite efetivo de tentativas multiplica pelo número de instâncias.
  Não é um problema hoje (uma instância só), mas é uma limitação conhecida.

## Onde encontrar no código

- Bloqueio de tentativas: `service/LoginAttemptService.java`
- Login, hash falso, geração de token: `service/AuthService.java`
- Verificação de perfil/status/senha alterada no banco a cada request:
  `config/JwtAuthenticationFilter.java`
- Geração/validação do JWT: `config/JwtService.java`
- Campos novos na entidade: `model/Usuario.java` (`trocarSenha`, `senhaAlteradaEm`)
- Migration que marca o admin do seed: `resources/db/migration/V6__Seguranca_E_Indices.sql`
- Endpoint de troca de senha: `controller/AuthController.java`, `PUT /api/auth/senha`
- Testes: `api/src/test/java/com/AJTBackend/service/AuthServiceTest.java`,
  `api/src/test/java/com/AJTBackend/service/LoginAttemptServiceTest.java`,
  `api/src/test/java/com/AJTBackend/config/JwtAuthenticationFilterTest.java` e,
  contra banco real, `api/src/test/java/com/AJTBackend/integracao/AutenticacaoIntegrationTest.java`

## Atualizações posteriores

- **2026-09-18 — [ADR-0013](0013-organizacao-do-codigo-e-tamanho-das-classes.md):** `AuthService.login`
  **deixou de ser `@Transactional`**. O BCrypt (~100 ms) rodava dentro de uma transação e segurava uma
  conexão do pool sem necessidade; agora só o `save` do `ultimoLogin` abre uma transação curta. O
  comportamento (bloqueio, hash falso, mensagens) não mudou.
- **Limitação conhecida (registrada de propósito):** o backend apenas **sinaliza** `trocarSenha: true`; ele
  não bloqueia as demais rotas enquanto a senha não é trocada. Quem obriga a tela de troca é o front.
  Bloquear no backend foi avaliado e adiado: em um banco novo o admin nasce com `trocarSenha = true`, e
  bloquear tudo quebraria fluxos automatizados (coleção Postman, smoke test) que fazem login e usam a API.
  Próximo passo natural: permitir apenas `/api/auth/**` enquanto `trocarSenha` for verdadeiro.
