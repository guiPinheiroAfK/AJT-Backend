# Mapa do código

Guia para quem precisa **entender ou alterar** o AJT-Backend. Responde "onde está X?", "o que faz cada
classe?" e "o que faço para adicionar um recurso novo?". As decisões e os porquês estão nas ADRs
([índice](README.md)); aqui está o **mapa**.

Todos os caminhos abaixo são relativos a `api/src/main/java/com/AJTBackend/` (código) ou
`api/src/test/java/com/AJTBackend/` (testes), salvo indicação.

## 1. Como uma requisição percorre o código

```
Postman / front
   │  POST /api/motoristas   (Authorization: Bearer <jwt>)
   ▼
config/JwtAuthenticationFilter   quem é? valida o token e carrega o usuário do banco
   ▼
config/SecurityConfig            esse perfil pode acessar essa rota? (403 se não)
   ▼
controller/MotoristaController   valida o JSON (@Valid) e chama UM método de service
   ▼
service/MotoristaService         regra de negócio, @Transactional, log, auditoria
   ▼
repository/MotoristaRepository  Spring Data gera o SQL a partir do nome do método
   ▼
PostgreSQL  (tabelas criadas pelo Flyway em resources/db/migration)
   ▼
service → dto/MotoristaResponseDTO → controller → JSON + status HTTP

qualquer exceção em qualquer camada  →  exception/GlobalExceptionHandler  →  JSON de erro padrão
```

## 2. Onde está... (consulta rápida)

| Pergunta | Onde |
|---|---|
| Como o login funciona? | `service/AuthService.login`, chamado por `controller/AuthController` |
| Como o token é criado e validado? | `config/JwtService` (criar/validar), `config/JwtAuthenticationFilter` (a cada requisição) |
| Como sei que é o primeiro acesso? | coluna `usuarios.trocar_senha` → campo `trocarSenha` da resposta do login; regra em `AuthService`, `UsuarioService`, migration `V6` (ADR-0004) |
| Quem pode acessar cada rota? | `config/SecurityConfig` (tabela no javadoc da classe) |
| Regra "motorista só muda o status da parada" | `service/ParadaOsService.validarAlteracaoPorMotorista` |
| Regra "ninguém exclui/rebaixa/desativa a si mesmo" | `service/UsuarioService` (usa `config/UsuarioLogado`) |
| Bloqueio por tentativas de login (429) | `service/LoginAttemptService` |
| Documento do passageiro cifrado | `config/CriptografiaConverter` (ligado em `model/Passageiro`) |
| Conversão de moeda (Feign) | `client/CotacaoClient` → `service/CotacaoService` (cache) → `service/ValorTransferService` |
| Paginação | `dto/PaginaResponseDTO`; `Pageable` recebido nos controllers |
| Validação de campos | Bean Validation nos `dto/*RequestDTO`; grupo `dto/validacao/OnPatch` para PATCH |
| Tratamento de erros | `exception/GlobalExceptionHandler`; 401/403 do Spring em `config/JsonAuthErrorHandler` |
| Auditoria (quem mudou o quê) | `service/AuditoriaService`, `model/LogAuditoria`, `GET /api/auditoria` |
| Documentação Swagger | anotações nos controllers/DTOs + `config/OpenApiConfig` |
| Perfis dev/prod, variáveis de ambiente | `resources/application.yml`, `.env.example`, `docker-compose.yml` |

## 3. Pacotes e classes

### `config/` — segurança e infraestrutura transversal (7)

| Classe | Responsabilidade |
|---|---|
| `SecurityConfig` | regras de acesso por rota/verbo/perfil, CORS, sessão stateless, ordem dos filtros |
| `JwtAuthenticationFilter` | lê o `Authorization: Bearer`, valida o JWT, confere no banco se o usuário está ativo e se a senha não mudou depois do token, e injeta o perfil **do banco** |
| `JwtService` | gera e valida o token (chave mínima de 32 caracteres, falha na subida se fraca) |
| `UsuarioLogado` | **único** ponto que lê o `SecurityContextHolder` (username, "é o usuário X?", "tem o perfil Y?") |
| `JsonAuthErrorHandler` | devolve 401/403 do Spring Security no mesmo JSON de erro da API |
| `CriptografiaConverter` | `AttributeConverter` JPA: AES-256-GCM, formato `v1:` + Base64(IV + cifra + tag) |
| `OpenApiConfig` | metadados do Swagger, esquema Bearer, respostas 401/403 globais |

### `client/` — integrações externas (1)

`CotacaoClient`: interface Feign para a Frankfurter API (`GET /latest?base=&symbols=`). A URL vem de
`ajt.cotacao.url`; timeouts em `spring.cloud.openfeign.client.config`.

### `controller/` — HTTP (11)

Sem regra de negócio: validam a entrada, delegam a **um** service e escolhem o status
(`201` no POST, `204` no DELETE).

| Controller | Base | Observações |
|---|---|---|
| `AuthController` | `/api/auth` | `POST /login` (público), `GET /me`, `PUT /senha` |
| `UsuarioController` | `/api/usuarios` | só ADMIN |
| `MotoristaController` | `/api/motoristas` | `GET /buscar?cnh=` |
| `VeiculoController` | `/api/veiculos` | `GET /buscar?placa=` |
| `PassageiroController` | `/api/passageiros` | `GET /buscar?nacionalidade=` |
| `TransferController` | `/api/transfers` | `GET /buscar?status=`, `GET /{id}/passageiros` |
| `PontoColetaController` | `/api/pontos-coleta` | `GET /transfer/{id}` |
| `OrdemServicoController` | `/api/ordens-servico` | `GET /buscar?status=` |
| `ParadaOsController` | `/api/paradas-os` | `GET /ordem-servico/{id}` |
| `CotacaoController` | `/api/cotacao` | `?de=USD&para=BRL` |
| `AuditoriaController` | `/api/auditoria` | somente leitura; ADMIN e GERENTE |

Todo recurso CRUD expõe o mesmo molde: `GET` listar (paginado), `GET /{id}`, `POST`, `PUT /{id}`
(substituição), `PATCH /{id}` (parcial, validado com `OnPatch`), `DELETE /{id}`.

### `service/` — regras de negócio (13)

| Service | Responsabilidade |
|---|---|
| `AuthService` | login (bloqueio, hash falso anti-timing, token) e troca de senha |
| `LoginAttemptService` | contador de falhas por IP + username (cache Caffeine) |
| `UsuarioService` | CRUD de usuários, hash BCrypt, `trocarSenha`, travas de autoproteção |
| `MotoristaService` / `VeiculoService` | CRUD com unicidade de CNH / placa |
| `PassageiroService` | CRUD; lista os passageiros de um transfer |
| `TransferService` | CRUD de transfers: vínculo com OS e passageiros, orquestra valor + auditoria; fora da transação a chamada de câmbio |
| `ValorTransferService` | decide o `valorBase` (informado, BRL ou convertido) e normaliza a moeda |
| `CotacaoService` | consulta a cotação via Feign, com cache de 30 min |
| `PontoColetaService` | CRUD de pontos de coleta de um transfer |
| `OrdemServicoService` | CRUD de OS (motorista e veículo opcionais) + auditoria |
| `ParadaOsService` | CRUD de paradas, vínculo N:N com transfers, restrição do perfil MOTORISTA |
| `AuditoriaService` | grava e consulta a trilha de auditoria |

Convenção: a classe inteira é `@Transactional(readOnly = true)`; cada método de escrita reafirma
`@Transactional`.

### `model/` — entidades (8) + auditoria (1) + enums (5)

| Entidade | Tabela | Relacionamentos |
|---|---|---|
| `Usuario` | `usuarios` | — |
| `Passageiro` | `passageiros` | — (documento cifrado) |
| `Motorista` | `motoristas` | `@OneToMany` OS |
| `Veiculo` | `veiculos` | `@OneToMany` OS |
| `OrdemServico` | `ordens_servico` | `@ManyToOne` Motorista e Veículo; `@OneToMany` Paradas e Transfers |
| `Transfer` | `transfers` | `@ManyToOne` OS; `@ManyToMany` Passageiros; `@OneToMany` Pontos de coleta |
| `PontoColeta` | `pontos_coleta` | `@ManyToOne` Transfer |
| `ParadaOs` | `paradas_os` | `@ManyToOne` OS; `@ManyToMany` Transfers |
| `LogAuditoria` | `logs_transfers` | — |

Enums (`model/enums/`): `Role`, `StatusTransfer`, `StatusOrdemServico`, `StatusParada`, `AcaoParada`
(gravados como texto, `EnumType.STRING`; ADR-0001).

### `repository/` — dados (9)

Um `JpaRepository` por entidade. Consultas por nome de método (`findByCnh`, `existsByPlaca`...). Exceções:
`ParadaOsRepository` usa `@EntityGraph` para evitar N+1 (ADR-0007) e `PassageiroRepository` tem uma
`@Query` JPQL (`findByTransferId`).

### `dto/` — contratos da API (24 + `validacao/OnPatch`)

- `XRequestDTO`: entrada, com Bean Validation e `@Schema(example)` para o Swagger.
- `XResponseDTO`: saída. **A entidade JPA nunca é devolvida.**
- Especiais: `PaginaResponseDTO<T>` (envelope de páginas), `ErroResponseDTO` (formato único de erro),
  `LoginRequestDTO`/`LoginResponseDTO`, `TrocaSenhaRequestDTO`, `Cotacao*DTO`, `AuditoriaResponseDTO`.

### `exception/` — erros (18)

```
RecursoNaoEncontradoException (abstract)  →  404
   ├─ MotoristaNaoEncontrada, VeiculoNaoEncontrada, PassageiroNaoEncontrada, TransferNaoEncontrada,
   │  PontoColetaNaoEncontrada, OrdemServicoNaoEncontrada, ParadaOsNaoEncontrada, UsuarioNaoEncontrada
RegistroDuplicadoException (abstract)     →  400
   └─ CnhJaCadastrada, PlacaJaCadastrada, UsernameJaCadastrada
RegraNegocioException                     →  400   (regra violada: "não pode excluir a si mesmo")
CredenciaisInvalidasException             →  401
MuitasTentativasException                 →  429
CotacaoIndisponivelException              →  502
GlobalExceptionHandler                    converte tudo (e 400 de validação, 403, 404 de rota, 409 de FK, 500)
```

Um handler cobre a classe-mãe: criar uma nova `XNaoEncontrada` **não** exige novo handler.

## 4. Banco de dados (Flyway)

| Migration | O que faz |
|---|---|
| `V1__Create_Base` | usuários, passageiros, motoristas, veículos |
| `V2__Create_Operacao` | ordens de serviço, transfers, pontos de coleta, `transfer_passageiros` (N:N) |
| `V3__Create_Auditoria` | tabela `logs_transfers` |
| `V4__Create_Paradas_OS` | paradas e `parada_os_transfers` (N:N) |
| `V5__Seed_Admin` | usuário `admin` inicial |
| `V6__Seguranca_E_Indices` | `trocar_senha`, `senha_alterada_em`, índices; marca o admin do seed para trocar a senha |
| `V7__Auditoria_Bigint` | `BIGINT` na auditoria + índice por tabela/data |

**Regra de ouro:** migration já aplicada **nunca** é editada (quebra o checksum do Flyway); mudanças viram
uma migration nova. O Hibernate roda em `ddl-auto: validate`: só confere, nunca altera o schema.

## 5. Testes (149) e onde ficam

| Nível | Pasta | Roda sem Docker |
|---|---|:---:|
| Unitário | `service/*Test`, `config/*Test` | sim |
| Camada web | `web/` | sim |
| Integração (PostgreSQL real) | `integracao/` | não |

Detalhes e justificativas: [ADR-0014](adr/0014-estrategia-de-testes-e-demonstracao.md).

## 6. Checklists

### Adicionar um recurso novo (ex.: "Hotel")

1. **Migration** `V8__Create_Hotel.sql`: tabela, chaves estrangeiras, índices das colunas de filtro.
2. **Entidade** em `model/` (`@Entity`, `@Table`, relacionamentos `LAZY`); enum em `model/enums/` se tiver status.
3. **Repository** em `repository/` (consultas por nome de método).
4. **DTOs** `HotelRequestDTO` (Bean Validation com tamanhos iguais aos da coluna + `@Schema(example)`) e
   `HotelResponseDTO`.
5. **Exceções**: `HotelNaoEncontradoException extends RecursoNaoEncontradoException` (e `...JaCadastrada` se
   houver campo único). **Não** precisa mexer no `GlobalExceptionHandler`.
6. **Service**: `@Transactional(readOnly = true)` na classe, `@Transactional` nas escritas, logs SLF4J,
   `PaginaResponseDTO` nas listagens; chame `AuditoriaService` se o recurso tiver ciclo de status.
7. **Controller**: `@Tag`, `@Operation` + `@ApiResponses` em cada método, `@Valid` no POST/PUT,
   `@Validated(OnPatch.class)` no PATCH, `201` no POST e `204` no DELETE.
8. **Segurança**: regra em `SecurityConfig` **e** a tabela do javadoc; uma linha em `AutorizacaoWebTest`.
9. **Testes**: unitário do service; `@MockitoBean` do novo service em `web/WebTestBase`; integração se
   houver relacionamento.
10. **Demonstração e docs**: pasta na coleção Postman, passos no `scripts/smoke-test.sh`, seção no `README.md`
    e uma **ADR** se houve decisão não óbvia.

### Adicionar um campo a um recurso existente

Migration (`ALTER TABLE ... ADD COLUMN`) → entidade → request/response DTO (com validação e exemplo) →
service (criar, PUT e PATCH) → testes → coleção Postman. Campo novo **opcional** não quebra o front.

### Mudar uma regra de permissão

`SecurityConfig` (as regras são lidas de cima para baixo e **a primeira que casa vale**) → tabela do
javadoc → `AutorizacaoWebTest` → ADR-0002.

### Antes de abrir o PR

`mvn test` (com Docker ligado, para rodar os 25 de integração) → `bash scripts/smoke-test.sh` contra a
API rodando → sem imports não usados → nenhuma classe passando de ~250 linhas sem justificativa.
