# ADR-0002: Matriz de permissões por perfil (RBAC via Spring Security)

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

A configuração original de segurança só restringia `/api/usuarios/**` a ADMIN;
todo o resto exigia apenas estar autenticado, qualquer perfil. Na prática, um
usuário MOTORISTA logado conseguia criar, editar e excluir passageiros, transfers,
ordens de serviço e veículos — sem relação nenhuma com o que a função dele deveria
poder fazer.

## Decisão

Definir uma matriz explícita de acesso por recurso, método HTTP e perfil, aplicada
via `authorizeHttpRequests` do Spring Security (não anotação `@PreAuthorize`
espalhada pelos métodos — a matriz inteira fica visível num lugar só):

| Recurso | Leitura (GET) | Escrita (POST/PUT/PATCH) | DELETE |
|---|---|---|---|
| `usuarios` | ADMIN | ADMIN | ADMIN |
| `motoristas`, `veiculos`, `ordens-servico` | todos autenticados | ADMIN, GERENTE | ADMIN, GERENTE |
| `paradas-os` | todos autenticados | ADMIN, GERENTE (+ MOTORISTA só via PATCH) | ADMIN, GERENTE |
| `passageiros`, `transfers`, `pontos-coleta` | todos autenticados | ADMIN, GERENTE, ATENDENTE | ADMIN, GERENTE |
| `cotacao`, `auth/me`, `auth/senha` | todos autenticados | todos autenticados | — |

O caso de MOTORISTA em `paradas-os` é especial: a rota HTTP libera o PATCH pra
esse perfil, mas o `ParadaOsService` valida programaticamente que ele só está
alterando o campo `statusParada` — qualquer outro campo no corpo da requisição
(local, horário, transfers vinculados) é rejeitado com `403`, mesmo o perfil tendo
acesso à rota. Ou seja: a autorização tem duas camadas — rota (Spring Security) e
campo (regra de negócio no service).

## Alternativas consideradas

- **`@PreAuthorize` em cada método de controller:** mais granular método a método,
  mas espalha a regra de acesso por dezenas de arquivos — fica impossível ver "quem
  pode fazer o quê" olhando um lugar só. Descartado em favor de centralizar no
  `SecurityConfig`.
- **Criar um perfil `MOTORISTA_PARADA` só pra esse caso de PATCH parcial:**
  descartado por ser complexidade desnecessária pra resolver um caso único — a
  validação de campo dentro do service resolve sem inflar o modelo de perfis.
- **Deixar o front esconder os botões e confiar só nisso:** nunca foi opção real
  — esconder botão no front não impede alguém de chamar a API direto (Postman,
  curl). A validação de verdade tem que estar no backend.

## Consequências

- Qualquer PR que altere permissão de uma rota precisa mexer no
  `SecurityConfig.java` — é o único lugar, o que facilita revisão, mas também
  significa que esquecer de atualizar essa matriz ao criar um recurso novo é um
  jeito fácil de deixar uma rota nova sem proteção nenhuma (fica liberada por
  `anyRequest().authenticated()`, então pelo menos exige login, mas não perfil
  certo). Vale checklist de PR.
- A dupla camada de autorização (rota + campo) em `paradas-os` é a única exceção
  ao padrão "tudo decidido no SecurityConfig" — quem for adicionar um caso
  parecido deve seguir o mesmo padrão (regra de campo dentro do service, não
  espalhar mais exceções na config).
- Testado via `AutorizacaoWebTest`, que roda a matriz inteira como tabela de casos
  (`@CsvSource`) — qualquer mudança na matriz sem atualizar esse teste quebra o
  build, o que é o comportamento desejado.

## Onde encontrar no código

- Regra de rota: `api/src/main/java/com/AJTBackend/config/SecurityConfig.java`
  (a mesma tabela acima está no javadoc da classe)
- Regra de campo do MOTORISTA em paradas: `service/ParadaOsService.java`, método
  `validarAlteracaoPorMotorista`
- Filtro que injeta o perfil vindo do banco (não do token) em cada requisição:
  `config/JwtAuthenticationFilter.java` — ver ADR-0004 para o porquê disso
- Teste da matriz inteira: `api/src/test/java/com/AJTBackend/web/AutorizacaoWebTest.java`,
  método `matrizDePermissoes`
- Teste da restrição de campo do motorista: `api/src/test/java/com/AJTBackend/service/ParadaOsServiceTest.java`

## Atualizações posteriores

Registro append-only: a decisão original acima continua valendo; abaixo, o que a estendeu depois.

- **2026-09-18 — [ADR-0011](0011-auditoria-de-escritas.md):** novo recurso `/api/auditoria`, somente leitura,
  permitido **apenas a ADMIN e GERENTE**. A regra `/api/auditoria/**` fica antes da regra genérica de GET
  (a primeira que casa vale). Linha nova da matriz: `auditoria | ADMIN, GERENTE | — | —`.
- **2026-09-18 — [ADR-0013](0013-organizacao-do-codigo-e-tamanho-das-classes.md):** a regra de campo do
  MOTORISTA (`ParadaOsService.validarAlteracaoPorMotorista`) agora pergunta a `UsuarioLogado.temPerfil(...)`
  em vez de ler o `SecurityContextHolder` diretamente. Comportamento idêntico.
- `GET /api/transfers/{id}/passageiros` ([ADR-0010](0010-passageiros-no-transfer-e-os-como-relacionamento.md))
  é leitura e segue a regra geral de GET (qualquer perfil autenticado).
