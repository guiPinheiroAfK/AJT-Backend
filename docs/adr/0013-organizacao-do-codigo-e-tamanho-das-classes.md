# ADR-0013: Organização do código em camadas e regras de tamanho de classe

- **Status:** Aceito
- **Data:** 2026-09-18

## Contexto

Com o projeto crescendo (novos recursos, regras de segurança, integração externa, auditoria), o risco
clássico é o código concentrar-se em poucas classes enormes ("classes-deus") que ninguém tem coragem de
tocar. Foi feita uma auditoria de tamanho e responsabilidade antes da entrega final. Fotografia do código
**depois** da revisão:

| Pacote | Classes | Linhas | Papel |
|---|---:|---:|---|
| `controller` | 11 | 802 | traduz HTTP ↔ chamada de service |
| `service` | 13 | 1.584 | regras de negócio e transações |
| `model` | 14 | 456 | entidades JPA (8) + auditoria (1) + enums (5) |
| `dto` | 25 | 563 | contratos de entrada e saída da API |
| `exception` | 18 | 297 | exceções de domínio + tratamento global |
| `config` | 7 | 513 | segurança, JWT, criptografia, OpenAPI |
| `repository` | 9 | 120 | acesso a dados (Spring Data) |
| `client` | 1 | 18 | integração externa (Feign) |
| **Total** | **99** | **4.368** | média de ~44 linhas por classe |

As maiores classes: `TransferService` (227), `ParadaOsService` (202), `UsuarioService` (182),
`GlobalExceptionHandler` (171), `OrdemServicoService` (140). **Nenhuma passa de ~230 linhas.**

A auditoria identificou, porém, três problemas de coesão que não eram de tamanho, mas de responsabilidade:

1. `TransferService` (264 linhas, a maior) misturava persistência, **regra de conversão de moeda com chamada
   externa**, vínculo de passageiros e auditoria.
2. A lógica "monta a mensagem de mudança de status" estava **duplicada** em `TransferService` e
   `OrdemServicoService`.
3. Três services (`UsuarioService`, `ParadaOsService`, `AuditoriaService`) liam o `SecurityContextHolder`
   **diretamente e cada um do seu jeito**, espalhando um detalhe do Spring Security pela regra de negócio.

## Decisão

### 1. Camadas com responsabilidade única (já vigentes, agora explicitadas)

```
HTTP → Controller → Service → Repository → PostgreSQL
                      │
                      ├─ regra de negócio, @Transactional, auditoria, chamadas externas
                      └─ nunca devolve entidade: sempre DTO
```

- **Controller:** só HTTP. Recebe o corpo (`@Valid`), delega para **um** método de service e escolhe o
  status. Sem `if` de negócio, sem `try/catch`, sem acesso a repositório.
- **Service:** toda regra de negócio e toda fronteira transacional. É o único lugar onde
  `@Transactional` aparece. Um service cuida de **um agregado** (motorista, transfer...).
- **Repository:** interfaces Spring Data; consultas por nome de método. Nenhum SQL escrito à mão, exceto
  uma `@Query` JPQL (`findByTransferId`).
- **DTO:** um `*RequestDTO` (entrada, com Bean Validation) e um `*ResponseDTO` (saída) por recurso. A
  entidade JPA nunca cruza a fronteira da API.
- **Exception:** exceções de domínio nomeadas; um único `GlobalExceptionHandler` as converte em JSON.

### 2. Regras práticas de tamanho e coesão

- **Sinal de alerta: classe acima de ~250 linhas ou que precise de "e" para descrever** ("faz X *e* Y").
  Não é um limite mecânico, é o gatilho para perguntar se há uma responsabilidade extraível.
- **Uma dependência externa (rede, criptografia, contexto de segurança) vive atrás de uma classe própria**,
  não espalhada pelos services.
- **Lógica repetida em dois services vira método de uma classe compartilhada** (ex.: auditoria).
- **Conhecimento de framework (Spring Security) não entra na regra de negócio.**

### 3. Extrações feitas nesta revisão

| Nova / alterada | O que passou a ser | Resolve |
|---|---|---|
| `service/ValorTransferService` | decide o `valorBase` do transfer (respeita o informado, BRL, conversão via `CotacaoService`, falha da API → `null`) e normaliza o código da moeda | tira a regra de moeda do `TransferService` (264 → 227 linhas) e a torna testável sozinha |
| `AuditoriaService.registrarAtualizacao(...)` | monta "acao: status A -> B" quando o status mudou | elimina a duplicação entre `TransferService` e `OrdemServicoService` |
| `config/UsuarioLogado` | **único** ponto que lê o `SecurityContextHolder`: `usernameOuSistema()`, `ehUsuario(username)`, `temPerfil(role)` | remove o acesso estático de 3 services; regras de "ninguém se exclui" e "motorista só muda status" passam a depender de uma abstração |
| `AuthService.login` | deixou de ser `@Transactional` | o BCrypt (~100 ms) não segura mais uma conexão do pool (ver ADR-0004) |

### 4. Convenções de nome e de pasta

- Pacote **por camada** (não por recurso): `controller/`, `service/`, `dto/`... Cada recurso aparece em
  cada camada com o mesmo prefixo (`Motorista` → `MotoristaController`, `MotoristaService`,
  `MotoristaRepository`, `MotoristaRequestDTO`, `MotoristaResponseDTO`, `MotoristaNaoEncontradoException`).
  Achar "tudo de motorista" é um `grep`/busca por prefixo.
- Sufixos obrigatórios: `Controller`, `Service`, `Repository`, `RequestDTO`, `ResponseDTO`, `Exception`,
  `Converter`, `Test`, `IntegrationTest`, `WebTest`.
- Comentários em minúsculas, explicando o **porquê**; todo teste tem, no topo, o bloco *"o que testa / como
  rodar / por que existe"*.
- Imports em ordem alfabética (bloco geral, depois `java.*`, depois estáticos); sem imports não usados.
- Testes espelham os pacotes: `service/XService` ↔ `service/XServiceTest`.

## Alternativas consideradas

- **Classe base genérica de CRUD (`CrudService<E, ReqDTO, ResDTO>`).** Os services de motorista, veículo,
  passageiro e ponto de coleta são parecidos (listar, buscar, criar, PUT, PATCH, deletar). Uma base genérica
  eliminaria ~300 linhas, mas trocaria código repetido **e legível** por generics e ganchos (`aoCriar`,
  `validarDuplicidade`) difíceis de seguir, e cada recurso tem regras próprias (CNH única, placa única,
  documento cifrado, motorista só muda status). Escolheu-se **repetição explícita** por clareza; se um
  quinto recurso idêntico surgir, reavaliar.
- **Subpacotes `dto.request` / `dto.response`.** Organizaria melhor os 25 DTOs, mas exigiria reescrever
  imports em quase todo o projeto e quebrar a associação por prefixo/sufixo já descrita. Com 25 arquivos
  bem nomeados a pasta plana continua navegável; reavaliar acima de ~40.
- **MapStruct para `entidade ↔ DTO`.** Cada service tem seu `toResponseDTO` privado (5–10 linhas).
  Uma dependência de geração de código não se paga nesse tamanho.
- **Pacote por recurso (`motorista/`, `transfer/`...).** É defensável (alta coesão por feature), mas
  divergiria da estrutura já adotada, das ADRs anteriores e do que o time conhece.
- **Nada extrair, "está tudo abaixo de 250 linhas".** O problema encontrado era de coesão, não de tamanho:
  uma classe de 264 linhas mistura mais responsabilidades que duas de 130.

## Consequências

- **Mais classes pequenas** (99 hoje) em vez de poucas grandes. O custo é navegar entre arquivos; o
  ganho é que cada classe cabe na cabeça e tem teste próprio (`ValorTransferServiceTest`,
  `UsuarioLogadoTest`, `AuditoriaServiceTest`).
- **Regras de segurança testáveis sem Spring:** as travas de autoproteção (ADR-0002/0004) dependem de
  `UsuarioLogado`, uma classe de 3 métodos, e os testes unitários apenas a instanciam.
- **Repetição consciente nos CRUDs.** Corrigir um bug de "PUT não valida duplicidade" pode exigir mexer em
  mais de um service. Aceito em troca de legibilidade.
- **Guia para o futuro:** ao adicionar recurso ou regra, seguir o checklist de `docs/mapa-do-codigo.md`.
  Quem ultrapassar o gatilho de ~250 linhas deve extrair antes de crescer mais.

## Onde encontrar no código

- Nova classe de moeda: `service/ValorTransferService.java` (+ `service/ValorTransferServiceTest.java`)
- Único acesso ao usuário logado: `config/UsuarioLogado.java` (+ `config/UsuarioLogadoTest.java`); usado por
  `service/UsuarioService.java`, `service/ParadaOsService.java`, `service/AuditoriaService.java`
- Auditoria compartilhada: `service/AuditoriaService.java` (`registrarAtualizacao`)
- Service que perdeu responsabilidades: `service/TransferService.java`
- Mapa completo de classes e checklist: `docs/mapa-do-codigo.md`
