# Mapa do código

Guia para quem precisa **entender ou alterar** o AJT-Backend. Responde "onde está X?", "o que faz cada
classe?" e "o que faço para adicionar um recurso novo?". As decisões e os porquês estão nas ADRs
([índice](README.md)); aqui está o **mapa**.

Todos os caminhos abaixo são relativos a `api/src/main/java/com/ajtbackend/` (código) ou
`api/src/test/java/com/ajtbackend/` (testes), salvo indicação.

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
| Auditoria (quem mudou o quê) | `service/AuditoriaService`, `model/LogAuditoria`, `GET /api/auditoria` (com `registroId`: histórico de um registro) |
| Documentação Swagger | anotações nos controllers/DTOs + `config/OpenApiConfig` |
| Perfis dev/prod, variáveis de ambiente | `resources/application.yml`, `.env.example`, `docker-compose.yml` |
| Filtros das listagens (status, período, texto) | `repository/Especificacoes` + `dto/Filtro*DTO`; montados no `listar` de cada service (ADR-0017) |
| Edição simultânea (409) | coluna `versao` + `@Version`; conferência em `service/Versoes` (cadastros) e no próprio service de transfer e OS (ADR-0016) |
| Motorista só vê as próprias OS | `service/AcessoMotorista`; vínculo `usuarios.motorista_id` (V11); `GET /api/ordens-servico/minhas` (ADR-0018) |
| Painel do dia e faturamento | `controller/PainelController` → `service/PainelService` (ADR-0019) |
| PDF da OS | `GET /api/ordens-servico/{id}/pdf` → `service/RelatorioOrdemServicoService` (ADR-0020) |

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

### `controller/` — HTTP (12)

Sem regra de negócio: validam a entrada, delegam a **um** service e escolhem o status
(`201` no POST, `204` no DELETE).

| Controller | Base | Observações |
|---|---|---|
| `AuthController` | `/api/auth` | `POST /login` (público), `GET /me`, `PUT /senha` |
| `UsuarioController` | `/api/usuarios` | só ADMIN |
| `MotoristaController` | `/api/motoristas` | `GET /buscar?cnh=` |
| `VeiculoController` | `/api/veiculos` | `GET /buscar?placa=` |
| `PassageiroController` | `/api/passageiros` | `GET /buscar?nacionalidade=` |
| `TransferController` | `/api/transfers` | `GET /buscar?status=`, `GET /{id}/passageiros`; filtros `?status&dataInicio&dataFim&busca&osId&semOs` |
| `PontoColetaController` | `/api/pontos-coleta` | `GET /transfer/{id}` |
| `OrdemServicoController` | `/api/ordens-servico` | `GET /buscar?status=`, `GET /minhas?data=` (só MOTORISTA), `GET /{id}/pdf`; filtros `?status&dataInicio&dataFim&motoristaId&veiculoId` |
| `ParadaOsController` | `/api/paradas-os` | `GET /ordem-servico/{id}` |
| `CotacaoController` | `/api/cotacao` | `?de=USD&para=BRL` |
| `AuditoriaController` | `/api/auditoria` | somente leitura; ADMIN e GERENTE; `?tabela=&registroId=` |
| `PainelController` | `/api/painel` | `GET /resumo?data=`; faturamento só para ADMIN e GERENTE |

Passageiros (`?nome&nacionalidade`) e motoristas (`?nome&cnh`) também aceitam filtro na listagem.

Todo recurso CRUD expõe o mesmo molde: `GET` listar (paginado), `GET /{id}`, `POST`, `PUT /{id}`
(substituição), `PATCH /{id}` (parcial, validado com `OnPatch`), `DELETE /{id}`.

### `service/` — regras de negócio (17)

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
| `AuditoriaService` | grava e consulta a trilha de auditoria (status, valor e histórico por registro) |
| `AcessoMotorista` | regra da HU07: o MOTORISTA só vê as OS do cadastro ligado ao usuário dele (ADR-0018) |
| `PainelService` | resumo do dia: contagens por status e faturamento só para ADMIN/GERENTE (ADR-0019) |
| `RelatorioOrdemServicoService` | PDF da OS com OpenPDF, sem valores nem documentos (ADR-0020) |
| `Versoes` | confere a versão enviada pelo cliente nos cadastros e lança 409 se estiver velha (ADR-0016) |

Convenção: a classe inteira é `@Transactional(readOnly = true)`; cada método de escrita reafirma
`@Transactional`.

### `model/` — entidades (8) + auditoria (1) + enums (5)

| Entidade | Tabela | Relacionamentos |
|---|---|---|
| `Usuario` | `usuarios` | `@OneToOne` Motorista (só perfil MOTORISTA, V11) |
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

### `repository/` — dados (9 + `Especificacoes`)

Um `JpaRepository` por entidade. Consultas por nome de método (`findByCnh`, `existsByPlaca`...). Exceções:
`ParadaOsRepository` usa `@EntityGraph` para evitar N+1 (ADR-0007), `PassageiroRepository` tem uma
`@Query` JPQL (`findByTransferId`), e `TransferRepository`/`OrdemServicoRepository` têm as consultas
agregadas do painel (`contarPorStatusNaData`, `somarValorBaseNoPeriodo`). Transfers, OS, passageiros e
motoristas estendem `JpaSpecificationExecutor` para os filtros; as peças ficam em `Especificacoes` (ADR-0017).

### `dto/` — contratos da API (30 + `validacao/OnPatch`)

- `XRequestDTO`: entrada, com Bean Validation e `@Schema(example)` para o Swagger; traz `versao` opcional (ADR-0016).
- `XResponseDTO`: saída, com a `versao` atual. **A entidade JPA nunca é devolvida.**
- `Filtro*DTO`: parâmetros de filtro da listagem (transfer, OS, passageiro, motorista), recebidos com `@ParameterObject`.
- Especiais: `PaginaResponseDTO<T>` (envelope de páginas), `ErroResponseDTO` (formato único de erro),
  `LoginRequestDTO`/`LoginResponseDTO`, `TrocaSenhaRequestDTO`, `Cotacao*DTO`, `AuditoriaResponseDTO`,
  `PainelResumoDTO`, `OrdemServicoDoDiaDTO` (OS com as paradas, usado em `/minhas`).

### `exception/` — erros (19)

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
ConflitoDeVersaoException                 →  409   (versão velha; o @Version barrando gravação simultânea também vira 409)
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
| `V8__Auditoria_Por_Registro` | índice `(tabela_afetada, registro_id, data_hora)` para o histórico de um registro |
| `V9__Versao_Concorrencia` | coluna `versao` nas 8 tabelas editáveis (lock otimista, ADR-0016) |
| `V10__Status_Transfer_Com_Os` | corrige transfers antigos: quem está numa OS e ainda estava `AGUARDANDO_OS` passa a `CONFIRMADO` |
| `V11__Usuario_Motorista` | `usuarios.motorista_id` (único, `ON DELETE SET NULL`) para a HU07 (ADR-0018) |
| `V12__Passageiro_Documento_Hash` | `passageiros.documento_hash` (HMAC do documento) para recusar documento duplicado sem decifrar; os antigos são preenchidos pelo `DocumentoHashBackfill` |

**Regra de ouro:** migration já aplicada **nunca** é editada (quebra o checksum do Flyway); mudanças viram
uma migration nova. O Hibernate roda em `ddl-auto: validate`: só confere, nunca altera o schema.

## 5. Testes (647 métodos, 889 execuções) e onde ficam

| Nível | Pasta | Base | Execuções | Roda sem Docker |
|---|---|---|---:|:---:|
| Unitário | `config/*Test`, `service/*Test` | nenhuma (Mockito) | 56 + 323 | sim |
| Repositório (`@DataJpaTest`, PostgreSQL real) | `repository/` | `RepositorioTestBase` | 72 | não |
| Camada web (`@WebMvcTest`) | `web/` | `WebTestBase`, `ErrosWebTestBase` | 363 | sim |
| Integração (`@SpringBootTest`, PostgreSQL real) | `integracao/` | `IntegracaoTestBase` | 75 | não |

Todo teste se chama `deve…_quando…` (ou `naoDeve…_quando…`) e tem os blocos `// given`, `// when` e
`// then`, com uma ação só no `when`. Detalhes e justificativas: [ADR-0014](adr/0014-estrategia-de-testes-e-demonstracao.md)
e [ADR-0021](adr/0021-estrategia-de-testes-v2-slices-e-bdd.md).

Contra a API rodando: `scripts/smoke-test.sh` (sequência da demonstração) e `scripts/api/run-all.sh` (um
script por recurso, caminho feliz e triste, roda duas vezes seguidas sem sobrar dado, ADR-0022).

## 6. Checklists

### Adicionar um recurso novo (ex.: "Hotel")

1. **Migration** `V12__Create_Hotel.sql` (a próxima livre): tabela com coluna `versao BIGINT NOT NULL DEFAULT 0`,
   chaves estrangeiras, índices das colunas de filtro.
2. **Entidade** em `model/` (`@Entity`, `@Table`, relacionamentos `LAZY`, `@Version` no campo `versao`); enum em
   `model/enums/` se tiver status.
3. **Repository** em `repository/` (consultas por nome de método; `JpaSpecificationExecutor` se a listagem
   tiver filtros, com as peças de `Especificacoes`).
4. **DTOs** `HotelRequestDTO` (Bean Validation com tamanhos iguais aos da coluna + `@Schema(example)` + `versao`
   opcional), `HotelResponseDTO` (com `versao`) e `FiltroHotelDTO` se houver filtro.
5. **Exceções**: `HotelNaoEncontradoException extends RecursoNaoEncontradoException` (e `...JaCadastrada` se
   houver campo único). **Não** precisa mexer no `GlobalExceptionHandler`.
6. **Service**: `@Transactional(readOnly = true)` na classe, `@Transactional` nas escritas, logs SLF4J,
   `PaginaResponseDTO` nas listagens, `Versoes.conferir` + `flush()` no PUT e no PATCH; chame `AuditoriaService`
   se o recurso tiver ciclo de status.
7. **Controller**: `@Tag`, `@Operation` + um `@ApiResponse` por status em cada método, `@Valid` no POST/PUT,
   `@Validated(OnPatch.class)` no PATCH, `@ParameterObject` no filtro, `201` no POST e `204` no DELETE.
8. **Segurança**: regra em `SecurityConfig` **e** a tabela do javadoc; uma linha em `AutorizacaoWebTest` e em
   `scripts/api/test-permissoes.sh`.
9. **Testes** (padrão `deve…_quando…` com `// given / when / then`, ADR-0021): unitário do service;
   `@MockitoBean` do novo service em `web/WebTestBase` e um `HotelErrosWebTest`; `@DataJpaTest` em
   `repository/` se houver consulta própria; integração só se precisar de HTTP + banco juntos.
10. **Demonstração e docs**: pasta na coleção Postman, passos no `scripts/smoke-test.sh`, um
    `scripts/api/test-hoteis.sh` (feliz e triste, sufixo único e limpeza no fim), seção no `README.md` e uma
    **ADR** se houve decisão não óbvia.

### Adicionar um campo a um recurso existente

Migration (`ALTER TABLE ... ADD COLUMN`) → entidade → request/response DTO (com validação e exemplo) →
service (criar, PUT e PATCH) → testes → coleção Postman. Campo novo **opcional** não quebra o front.

### Mudar uma regra de permissão

`SecurityConfig` (as regras são lidas de cima para baixo e **a primeira que casa vale**) → tabela do
javadoc → `AutorizacaoWebTest` → ADR-0002.

### Antes de abrir o PR

`mvn verify` (com Docker ligado, para rodar repositório e integração; a cobertura do JaCoCo não pode cair) →
`bash scripts/api/run-all.sh` e `bash scripts/smoke-test.sh` contra a API rodando → sem imports não usados →
nenhuma classe passando de ~250 linhas sem justificativa.
