# Catálogo de testes do backend

Este documento explica **o que cada classe de teste cobre**, **a quais requisitos** (HU, RF, RNF)
ela responde, **quais caminhos tristes** (erros, entradas inválidas, regras violadas) ela exercita
e **qual status HTTP** a API devolve em cada caso.

Ele é o mapa para ler os testes. O detalhe de cada cenário está no próprio código: cada classe
abre com um cabeçalho **o que testa / como rodar / por que existe**, e cada teste segue
**given / when / then** com o nome no padrão `deveX_quandoY` (ex.:
`deveRetornar409_quandoPutTrazVersaoVelha`).

## Números

| | |
|---|---|
| Classes de teste | 55 (mais 2 classes base, sem teste próprio) |
| Métodos de teste | 660 |
| Execuções (com os casos dos testes parametrizados) | 914 |
| Cobertura (JaCoCo) | **100%** de linhas, branches, instruções e métodos |
| SonarQube | 0 bugs, 0 vulnerabilidades, 0 code smells, 0% de duplicação, nota A em tudo |

## Como rodar

```bash
cd api
mvn test           # roda tudo; os testes de banco precisam do Docker
mvn verify         # testes + relatório de cobertura em target/site/jacoco/index.html
```

Sem Docker, as classes de `repository/` e `integracao/` são **puladas** (não falham o build);
as de `service/`, `config/`, `exception/` e `web/` rodam sempre.

## Níveis de teste

| Nível | Pasta | O que sobe | Para quê |
|---|---|---|---|
| Unitário | `service/`, `config/`, `exception/` | só a classe testada; dependências mockadas | regra de negócio, um caso por regra, rápido |
| Camada web | `web/` (`@WebMvcTest`) | controllers, validação, segurança e o `GlobalExceptionHandler`; services mockados | contrato HTTP: rota, verbo, corpo, permissão e status de cada resposta |
| Repositório | `repository/` (`@DataJpaTest`) | JPA, Flyway e um PostgreSQL 16 real (Testcontainers) | SQL de verdade: consultas, filtros, constraints, criptografia na coluna |
| Integração | `integracao/` (`@SpringBootTest`) | a aplicação inteira, PostgreSQL real e a API de câmbio simulada (WireMock) | fluxos ponta a ponta que nenhum mock reproduz |

Detalhes e justificativas: [ADR-0014](adr/0014-estrategia-de-testes-e-demonstracao.md) e
[ADR-0021](adr/0021-estrategia-de-testes-v2-slices-e-bdd.md).

---

## 1. Requisitos → testes

### Histórias de usuário

| Requisito | Onde está testado |
|---|---|
| **HU01** Login com usuário e senha, conforme o perfil | `AuthServiceTest`, `JwtServiceTest`, `JwtAuthenticationFilterTest`, `LoginAttemptServiceTest`, `AutenticacaoIntegrationTest`, `TratamentoGlobalErrosWebTest` (401 e 429), `ValidacaoWebTest` (login sem campos) |
| **HU02** Cadastrar passageiros com documento | `PassageiroServiceTest`, `PassageiroErrosWebTest`, `PassageiroRepositoryTest`, `PersistenciaIntegrationTest`, `DocumentoHashTest` |
| **HU03** Cadastrar motoristas e veículos | `MotoristaServiceTest`, `MotoristaErrosWebTest`, `MotoristaRepositoryTest`, `VeiculoServiceTest`, `VeiculoErrosWebTest`, `VeiculoRepositoryTest` |
| **HU04** Agendar transfer (origem, destino, data/hora, valor, passageiros) | `TransferServiceTest`, `TransferServiceRegrasTest`, `TransferErrosWebTest`, `ValidacaoWebTest`, `RelacionamentosIntegrationTest` |
| **HU05** Pontos de coleta com ordem de parada | `PontoColetaServiceTest`, `PontoColetaErrosWebTest`, `PontoColetaRepositoryTest`, `ParadaOsServiceTest`, `ParadaOsServiceCrudTest`, `ParadaOsErrosWebTest` |
| **HU06** Ordem de serviço agrupando transfers do dia | `OrdemServicoServiceTest`, `OrdemServicoErrosWebTest`, `TransferServiceTest` (vínculo com a OS), `RelacionamentosIntegrationTest` |
| **HU07** Motorista vê só as próprias OS do dia, com as paradas | `AcessoMotoristaTest`, `FiltrosEMinhasOsIntegrationTest`, `OrdemServicoServiceTest`, `ParadaOsServiceTest`, `ParadaOsRepositoryTest`, `UsuarioServiceTest`, `UsuarioRepositoryTest` |
| **HU08** Painel com indicadores do dia | `PainelServiceTest`, `ConsultasDoPainelRepositoryTest`, `RelacionamentosIntegrationTest` (painel com e sem faturamento) |
| **HU09** Gerenciar usuários e perfis | `UsuarioServiceTest`, `UsuarioServiceCrudTest`, `UsuarioErrosWebTest`, `UsuarioRepositoryTest`, `AutorizacaoWebTest`, `UsuarioLogadoTest` |
| **HU10** Relatório PDF da OS | `RelatorioOrdemServicoServiceTest`, `RelacionamentosIntegrationTest` (PDF real), `OrdemServicoErrosWebTest` (PDF de OS inexistente), `FiltrosEMinhasOsIntegrationTest` (PDF da OS de outro motorista) |
| **HU11** Histórico de status e valor do transfer | `AuditoriaServiceTest`, `LogAuditoriaRepositoryTest`, `RelacionamentosIntegrationTest` (trilha gravada e consultada), `TransferServiceTest` (auditoria de cada escrita) |

### Requisitos funcionais

| Requisito | Onde está testado |
|---|---|
| **RF001** Autenticação com token JWT | `JwtServiceTest`, `JwtAuthenticationFilterTest`, `AuthServiceTest`, `AutenticacaoIntegrationTest` |
| **RF002** CRUD de passageiros | `PassageiroServiceTest`, `PassageiroErrosWebTest`, `ContratoSucessoWebTest` |
| **RF003** CRUD de motoristas | `MotoristaServiceTest`, `MotoristaErrosWebTest`, `ContratoSucessoWebTest` |
| **RF004** CRUD de veículos | `VeiculoServiceTest`, `VeiculoErrosWebTest`, `ContratoSucessoWebTest` |
| **RF005** CRUD de pontos de coleta | `PontoColetaServiceTest`, `PontoColetaErrosWebTest`, `ContratoSucessoWebTest` |
| **RF006** Transfer com origem, destino, data, hora, valor e moeda | `TransferServiceTest`, `TransferErrosWebTest`, `ValidacaoWebTest`, `ValorTransferServiceTest` |
| **RF007** Um ou mais passageiros por transfer | `TransferServiceTest`, `TransferServiceRegrasTest`, `RelacionamentosIntegrationTest`, `PassageiroRepositoryTest` |
| **RF008** Vários pontos de coleta ordenados no transfer | `PontoColetaRepositoryTest`, `PontoColetaServiceTest`, `ParadaOsRepositoryTest` |
| **RF009** OS agrupando por motorista, veículo e data | `OrdemServicoServiceTest`, `TransferServiceTest`, `TransferServiceRegrasTest`, `RelacionamentosIntegrationTest` |
| **RF010** Atualizar status de transfers e OS | `TransferServiceTest`, `TransferServiceRegrasTest`, `OrdemServicoServiceTest`, `ParadaOsServiceTest`, `ValidacaoWebTest` (status fora do enum) |
| **RF011** Acesso restrito por perfil | `AutorizacaoWebTest` (matriz rota a rota), `UsuarioLogadoTest`, `ParadaOsServiceCrudTest` (motorista só mexe no status), `PersistenciaIntegrationTest`, `RelacionamentosIntegrationTest` |
| **RF012** Painel com indicadores | `PainelServiceTest`, `ConsultasDoPainelRepositoryTest` |
| **RF013** PDF da ordem de serviço | `RelatorioOrdemServicoServiceTest`, `RelacionamentosIntegrationTest` |
| **RF014** Auditoria de preço e status do transfer | `AuditoriaServiceTest`, `LogAuditoriaRepositoryTest`, `RelacionamentosIntegrationTest` |
| **RF015** Paginação e filtros nas listagens | `EspecificacoesTest`, `FiltrosNoBancoRepositoryTest`, `FiltrosEMinhasOsIntegrationTest`, `ValidacaoWebTest` (paginação padrão e máxima), `PersistenciaIntegrationTest` (totais e ordenação) |
| **RF016** Valor também na moeda de origem | `ValorTransferServiceTest`, `CotacaoServiceTest`, `CotacaoIntegrationTest` |

### Requisitos não funcionais

| Requisito | Onde está testado |
|---|---|
| **RNF001** Senha com hash BCrypt | `UsuarioServiceTest` (`deveGravarOHashENuncaOTextoPuro_quandoCriaUsuario`), `AuthServiceTest` |
| **RNF002** API REST autenticada por JWT | `JwtServiceTest`, `JwtAuthenticationFilterTest`, `AutorizacaoWebTest`, `OpenApiConfigTest` |
| **RNF003** Interface responsiva | é do frontend; fora deste catálogo |
| **RNF004** Esquema versionado com Flyway | toda classe de `repository/` e `integracao/` sobe o banco rodando as migrations V1 a V12 com `ddl-auto=validate`: se uma entidade não bater com o esquema, o teste nem sobe |
| **RNF005** Listagens rápidas | não há teste de carga. O que é garantido por teste: sem consulta N+1 nas paradas (`ParadaOsRepositoryTest`), transfers carregados em lote e paginação com tamanho máximo (`ValidacaoWebTest`) |
| **RNF006** Executável em containers | não é teste automatizado; os testes de banco já rodam dentro de containers (Testcontainers) |
| **RNF007** Documento do passageiro criptografado | `CriptografiaConverterTest`, `DocumentoHashTest`, `PassageiroRepositoryTest`, `PersistenciaIntegrationTest`, `PassageiroServiceTest` (máscara pro motorista), `RelatorioOrdemServicoServiceTest` (documento nunca impresso) |
| **RNF008** Vários usuários ao mesmo tempo, sem perder dados | `VersaoCadastrosIntegrationTest`, `RelacionamentosIntegrationTest` (409 de versão), `TratamentoGlobalErrosWebTest` (409 do `@Version`), o 409 de versão velha em cada `*ErrosWebTest` e o `ConflitoDeVersao` em cada `*ServiceTest` |

---

## 2. Status HTTP → quando acontece → onde está testado

Todas as respostas de erro saem no mesmo formato (`timestamp`, `status`, `erro`, `mensagem`,
`detalhes`), montado pelo `GlobalExceptionHandler`. Os testes conferem o status **e** o corpo.

| Status | `erro` | Quando a API devolve | Onde está testado |
|---|---|---|---|
| **200 / 201 / 204** | — | leitura e edição / criação / exclusão com entrada válida | `ContratoSucessoWebTest` (63 endpoints, um caso por verbo e rota) |
| **400** | Erro de validação | campo obrigatório ausente, texto vazio, tamanho, formato (placa, moeda ISO, username), faixa (latitude, capacidade), enum inválido (lista os aceitos) e registro duplicado (CNH, placa, username, documento) | cada `*ErrosWebTest` (um caso por regra do DTO, no POST e no PUT), `ValidacaoWebTest` |
| **400** | Regra de negócio | CPF com dígito errado, período invertido no filtro, transfer em OS de outro dia ou cancelada, mudar a data de OS que tem transfers, se excluir ou rebaixar o próprio perfil, ligar a motorista um usuário que não é motorista | `*ErrosWebTest` e os `*ServiceTest` correspondentes |
| **400** | Parâmetro inválido | id com texto na URL, parâmetro obrigatório ausente, data em formato errado | `*ErrosWebTest`, `TratamentoGlobalErrosWebTest` |
| **400** | Requisição inválida | corpo ausente ou JSON malformado | `PassageiroErrosWebTest`, `ValidacaoWebTest`, `GlobalExceptionHandlerTest` |
| **401** | Falha na autenticação | sem token, token inválido ou expirado, senha errada | `AutorizacaoWebTest`, `AutenticacaoIntegrationTest`, `TratamentoGlobalErrosWebTest` |
| **403** | Acesso negado | perfil sem permissão na rota (ex.: atendente na auditoria, motorista no cadastro de passageiros) ou motorista mexendo em campo da parada que não é o status | `AutorizacaoWebTest`, `ParadaOsErrosWebTest`, `PersistenciaIntegrationTest`, `FiltrosEMinhasOsIntegrationTest` |
| **404** | Recurso não encontrado | id inexistente nos 4 verbos, recurso referenciado que não existe (OS, motorista, passageiro), rota inexistente, e a OS de outro motorista (para não revelar que ela existe) | todo `*ErrosWebTest`, `FiltrosEMinhasOsIntegrationTest`, `RelacionamentosIntegrationTest` |
| **405** | Método não permitido | verbo que a rota não aceita | `TratamentoGlobalErrosWebTest`, `AutorizacaoWebTest` |
| **409** | Conflito de edição | a versão enviada é mais velha que a gravada (outra pessoa salvou antes) | todo `*ErrosWebTest`, `VersaoCadastrosIntegrationTest`, `RelacionamentosIntegrationTest`, `TratamentoGlobalErrosWebTest` |
| **409** | Conflito de dados | excluir registro em uso (motorista ou veículo numa OS, passageiro num transfer) | `MotoristaErrosWebTest`, `VeiculoErrosWebTest`, `PassageiroErrosWebTest`, `OrdemServicoErrosWebTest`, `PersistenciaIntegrationTest` |
| **429** | Muitas tentativas | login bloqueado depois de errar a senha várias vezes | `TratamentoGlobalErrosWebTest`, `AutenticacaoIntegrationTest` |
| **500** | Erro interno | falha inesperada, sem vazar detalhe interno na resposta | `TratamentoGlobalErrosWebTest` |
| **502** | Serviço externo indisponível | API de câmbio fora do ar, com erro ou lenta (timeout) | `TransferErrosWebTest`, `CotacaoIntegrationTest` |

---

## 3. Classe a classe

Formato de cada entrada: **o que cobre**, **requisitos**, **caminhos tristes** e o
**número de testes** (métodos / execuções, contando os casos dos parametrizados).

### 3.1 Unitários — regras de negócio (`service/`)

**PassageiroServiceTest** — 32 métodos / 40 execuções
- Cobre: a validação do CPF (dígitos verificadores, com ou sem pontuação), o bloqueio de documento já usado por outro passageiro e a máscara do documento quando quem lê é o motorista.
- Requisitos: HU02, RF002, RNF007.
- Caminhos tristes: CPF inválido, nulo ou com todos os dígitos iguais; documento duplicado no cadastro, no PUT e no PATCH (mesmo com outra pontuação); id inexistente; versão velha. Em cada um, o teste também confere que nada foi gravado.

**MotoristaServiceTest** — 20 métodos / 20 execuções
- Cobre: CNH única no cadastro e na edição (PUT e PATCH, inclusive mantendo a mesma CNH) e o PATCH que só muda o que veio.
- Requisitos: HU03, RF003.
- Caminhos tristes: CNH já cadastrada, id ou CNH inexistentes, versão velha, exclusão de quem não existe.

**VeiculoServiceTest** — 19 métodos / 19 execuções
- Cobre: placa única no cadastro e na edição e o PATCH parcial.
- Requisitos: HU03, RF004.
- Caminhos tristes: placa já cadastrada, id ou placa inexistentes, versão velha, exclusão de quem não existe.

**PontoColetaServiceTest** — 17 métodos / 17 execuções
- Cobre: listagens, cadastro ligado ao transfer e PATCH campo a campo (inclusive trocando o transfer).
- Requisitos: HU05, RF005, RF008.
- Caminhos tristes: ponto ou transfer inexistentes (sem alterar o ponto), versão velha.

**TransferServiceTest** — 16 métodos / 16 execuções
- Cobre: a orquestração do transfer: OS referenciada, recálculo do valor em reais no PATCH, passageiros vinculados numa consulta só e a auditoria de cada escrita.
- Requisitos: HU04, HU06, HU11, RF006, RF007, RF009, RF010, RF014.
- Caminhos tristes: OS inexistente, passageiro inexistente, OS de outro dia, OS cancelada, versão velha.

**TransferServiceRegrasTest** — 15 métodos / 16 execuções
- Cobre: as bordas da regra do transfer com a OS (sair da OS sem estar confirmado, mudar só a data dentro da mesma OS, OS finalizada), o PATCH que troca só a moeda e a exclusão auditada.
- Requisitos: HU04, RF009, RF010.
- Caminhos tristes: id inexistente em cada operação (o 404 sai antes de consultar o câmbio), período invertido no filtro, entrada em OS finalizada.

**ValorTransferServiceTest** — 13 métodos / 13 execuções
- Cobre: o valor em reais do transfer: valor base informado é respeitado, BRL usa o valor original e moeda estrangeira converte pela cotação com duas casas (HALF_UP).
- Requisitos: RF006, RF016.
- Caminhos tristes: API de câmbio fora do ar (devolve null sem travar o cadastro), valor ausente, moeda em minúsculo ou nula.

**CotacaoServiceTest** — 5 métodos / 5 execuções
- Cobre: a consulta de câmbio: mesma moeda não chama a API e a resposta vem normalizada.
- Requisitos: RF016.
- Caminhos tristes: moeda que não vem na resposta, resposta sem a tabela de taxas e erro do client. Todos viram `CotacaoIndisponivelException` (502).

**OrdemServicoServiceTest** — 26 métodos / 26 execuções
- Cobre: filtros, "minhas OS do dia", status padrão ABERTA, PATCH parcial e a auditoria de cada escrita.
- Requisitos: HU06, HU07, RF009, RF010, RF015.
- Caminhos tristes: período invertido, motorista vendo OS de outro (404), motorista sem vínculo (lista vazia), motorista ou veículo inexistentes, versão velha, mudar o dia de OS que tem transfers.

**ParadaOsServiceTest** — 22 métodos / 25 execuções
- Cobre: a parada do ponto de vista de cada perfil. O motorista só muda o status e só das paradas das próprias OS; o gerente mexe em qualquer campo; os transfers são trocados numa consulta só.
- Requisitos: HU05, HU07, RF008, RF011.
- Caminhos tristes: motorista mexendo em outro campo junto com o status (403), parada de outro motorista (404), transfer inexistente na lista, parada ou OS inexistentes.

**ParadaOsServiceCrudTest** — 18 métodos / 24 execuções
- Cobre: o restante do CRUD da parada: status padrão PENDENTE, PATCH campo a campo e a listagem geral para cada perfil.
- Requisitos: HU05, HU07, RF011.
- Caminhos tristes: OS inexistente no cadastro e no PUT, versão velha e o motorista tentando mexer em cada um dos outros campos (403, um caso por campo).

**UsuarioServiceTest** — 23 métodos / 27 execuções
- Cobre: senha sempre em hash, username único, senha redefinida pelo admin derrubando as sessões e as travas da própria conta.
- Requisitos: HU09, HU07, RNF001.
- Caminhos tristes: cadastro sem senha, username de outro, se excluir, se rebaixar, se desativar, ligar motorista a usuário de outro perfil, motorista inexistente ou já ligado a outro usuário.

**UsuarioServiceCrudTest** — 19 métodos / 20 execuções
- Cobre: o restante do CRUD de usuário: cadastro ativo por padrão, PUT completo e PATCH de username e de motorista.
- Requisitos: HU09.
- Caminhos tristes: buscas inexistentes, cadastro com senha em branco, motorista inexistente no vínculo, versão velha. Também cobre o PUT sem `ativo`, que não muda a situação do usuário.

**AuthServiceTest** — 11 métodos / 11 execuções
- Cobre: o login (token com os dados certos e último acesso atualizado) e a troca de senha.
- Requisitos: HU01, RF001, RNF001.
- Caminhos tristes: username inexistente, senha errada e usuário inativo, que dão **o mesmo** erro genérico para não revelar qual dos três falhou; bloqueio por excesso de tentativas (nem consulta o banco); senha atual errada; nova senha igual à atual; usuário removido.

**LoginAttemptServiceTest** — 4 métodos / 8 execuções
- Cobre: o contador de tentativas que barra força bruta, valendo para o par IP + usuário.
- Requisitos: HU01, RF001.
- Caminhos tristes: bloqueio ao atingir o limite, sem bloquear o mesmo usuário em outro IP nem outro usuário no mesmo IP; reset depois de um login certo; username com outra caixa contando como o mesmo.

**AcessoMotoristaTest** — 9 métodos / 10 execuções
- Cobre: a regra da HU07 isolada. Quem não é motorista vê qualquer OS; o motorista só vê a OS do cadastro ligado ao usuário dele.
- Requisitos: HU07, RF011.
- Caminhos tristes: OS de outro motorista, OS ainda sem motorista, motorista sem cadastro ligado.

**PainelServiceTest** — 12 métodos / 14 execuções
- Cobre: o resumo do painel. Contagens por status (com zero nos que faltam), totais e faturamento do dia e do mês, que só ADMIN e GERENTE veem.
- Requisitos: HU08, RF012, RF011.
- Caminhos tristes: perfil que não gerencia não vê nem consulta o faturamento; dia sem nenhum registro; dia 1 do mês; data padrão no fuso de São Paulo.

**AuditoriaServiceTest** — 13 métodos / 13 execuções
- Cobre: a trilha de auditoria (tabela, registro, usuário e data) e as mensagens de mudança de status e de valor ("de A para B").
- Requisitos: HU11, RF014.
- Caminhos tristes: sem usuário logado (registra como "sistema"), valor apagado, mesmo valor com escala diferente (não conta como mudança), tabela em branco no filtro.

**RelatorioOrdemServicoServiceTest** — 6 métodos / 6 execuções
- Cobre: o PDF da OS em cada variação da operação. O teste lê o texto do PDF gerado.
- Requisitos: HU10, RF013, RNF007.
- Caminhos tristes: OS inexistente ou de outro motorista (404); OS vazia, sem motorista, veículo, paradas e transfers (imprime avisos, nunca "null"); campos vazios; documento do passageiro nunca impresso.

### 3.2 Unitários — segurança e infraestrutura (`config/`, `exception/`, raiz)

**JwtServiceTest** — 11 métodos / 11 execuções
- Cobre: geração e validação do token JWT.
- Requisitos: RF001, RNF002.
- Caminhos tristes: token expirado, assinado com outra chave ou malformado; chave curta ou ausente (a aplicação nem sobe); token emitido antes de uma troca de senha.

**JwtAuthenticationFilterTest** — 6 métodos / 8 execuções
- Cobre: o filtro que lê o header `Authorization` em cada requisição. A role usada vem do banco, e não do token.
- Requisitos: RF001, RF011, RNF002.
- Caminhos tristes: usuário inativo, removido ou com senha trocada depois do token; header sem token, com outro esquema (Basic) ou com token inválido, que nem chega a consultar o banco.

**UsuarioLogadoTest** — 13 métodos / 18 execuções
- Cobre: o único ponto que lê o usuário logado: o username, a comparação exata e a checagem de perfil.
- Requisitos: RF011.
- Caminhos tristes: ninguém logado ("sistema"), username parecido mas não igual, username nulo, authority sem o prefixo ROLE_, usuário sem nenhuma authority.

**CriptografiaConverterTest** — 15 métodos / 18 execuções
- Cobre: a cifra AES-256-GCM do documento do passageiro: ida e volta, IV aleatório e valor legado sem prefixo.
- Requisitos: RNF007.
- Caminhos tristes: decifrar com outra chave e dado adulterado (falha sem expor o documento), chave com tamanho errado ou fora de Base64, JVM sem o algoritmo.

**DocumentoHashTest** — 7 métodos / 7 execuções
- Cobre: o hash do documento (V12), usado para achar duplicados sem decifrar nada, e o backfill dos passageiros antigos.
- Requisitos: HU02, RNF007.
- Caminhos tristes: mesmo documento com pontuação, espaços ou caixa diferentes dá o mesmo hash; tipo ou chave diferentes dão outro; tipo e documento nulos; JVM sem HmacSHA256; backfill sem pendências.

**OpenApiConfigTest** — 2 métodos / 2 execuções
- Cobre: a documentação Swagger: esquema Bearer JWT e as respostas 401 e 403 em toda operação, menos no login.
- Requisitos: RNF002.

**GlobalExceptionHandlerTest** — 3 métodos / 3 execuções
- Cobre: as bordas do 400 de JSON ilegível que o MockMvc não produz.
- Caminhos tristes: valor inválido sem tipo de destino conhecido e enum inválido sem o caminho do campo. Nos dois, a resposta é a genérica, sem inventar nome de campo.

**AJTBackendApplicationTest** — 1 método / 1 execução
- Cobre: o `main` repassa a classe principal e os argumentos (como `--server.port`) para o Spring.

### 3.3 Camada web — contrato HTTP e caminhos tristes (`web/`)

Cada classe `*ErrosWebTest` segue o mesmo roteiro para um recurso:

1. **cada regra do DTO** no POST e no PUT (um caso parametrizado por regra) → 400 sem chamar o service;
2. **PATCH** com texto vazio → 400;
3. **id inexistente nos 4 verbos** (GET, PUT, PATCH, DELETE) → 404;
4. **duplicado** → 400; **versão velha** → 409; **registro em uso** na exclusão → 409;
5. **parâmetro ausente ou com tipo errado** → 400.

**ContratoSucessoWebTest** — 1 método / 63 execuções
- Cobre: o lado oposto dos `*ErrosWebTest`: cada um dos 63 endpoints, com entrada válida, responde com o status combinado (200 leitura e edição, 201 criação, 204 exclusão). Mudar um verbo ou um `@ResponseStatus` sem querer quebra o build.
- Requisitos: RF002 a RF005 e as rotas de transfers, OS, paradas, painel, auditoria, cotação e autenticação.

**PassageiroErrosWebTest** — 13 métodos / 28 execuções
- Requisitos: HU02, RF002.
- Caminhos tristes: cada regra do DTO (obrigatório, vazio, tamanho), corpo ausente ou malformado, id com texto na URL, id inexistente nos 4 verbos, CPF inválido (400 de regra de negócio), documento já cadastrado (400, **sem repetir o número na mensagem**) e passageiro em uso (409).

**MotoristaErrosWebTest** — 12 métodos / 28 execuções
- Requisitos: HU03, RF003.
- Caminhos tristes: regras do DTO (obrigatórios, tamanhos, coordenadas fora da faixa), id e CNH inexistentes, busca por CNH sem o parâmetro, CNH duplicada, versão velha e motorista em uso numa OS (409).

**VeiculoErrosWebTest** — 14 métodos / 34 execuções
- Requisitos: HU03, RF004.
- Caminhos tristes: regras do DTO (obrigatórios, tamanhos, **placa fora do padrão** ABC1234 / ABC1D23, capacidade zero, negativa, ausente ou em texto), id e placa inexistentes, placa duplicada, versão velha e veículo em uso (409).

**PontoColetaErrosWebTest** — 12 métodos / 26 execuções
- Requisitos: HU05, RF005, RF008.
- Caminhos tristes: transfer e coordenadas obrigatórios, latitude e longitude fora da faixa, ordem não positiva, horário em formato errado, transfer ou ponto inexistentes e versão velha.

**TransferErrosWebTest** — 16 métodos / 37 execuções
- Requisitos: HU04, RF006, RF007, RF009, RF016.
- Caminhos tristes: data e hora ausentes ou mal formatadas, valores negativos ou com casas demais, moeda fora do padrão ISO, passageiros acima do limite, filtros inválidos, OS de outro dia ou cancelada, OS, passageiro ou transfer inexistentes, câmbio fora do ar (502) e versão velha.

**OrdemServicoErrosWebTest** — 20 métodos / 20 execuções
- Requisitos: HU06, HU10, RF009, RF010, RF013, RF015.
- Caminhos tristes: data obrigatória e no formato ISO, status fora do enum (no corpo e na URL), filtro com data inválida ou período invertido, motorista, veículo ou OS inexistentes, mudar a data de OS com transfers, versão velha, OS com transfers na exclusão (409) e PDF de OS inexistente (404).

**ParadaOsErrosWebTest** — 13 métodos / 27 execuções
- Requisitos: HU05, HU07, RF008, RF011.
- Caminhos tristes: OS e ordem obrigatórias, ordem positiva, coordenadas, limite de transfers, ação ou status fora do enum, OS, transfer ou parada inexistentes, motorista mexendo em campo que não é o status (403) e versão velha.

**UsuarioErrosWebTest** — 17 métodos / 31 execuções
- Requisitos: HU09, RF011.
- Caminhos tristes: nome, formato do username, tamanho da senha, perfil ausente ou fora do enum; as regras do service (senha obrigatória no cadastro, não se excluir nem se rebaixar, vínculo com motorista só para o perfil Motorista); username duplicado, id ou username inexistentes e versão velha.

**ValidacaoWebTest** — 14 métodos / 14 execuções
- Cobre: a validação na fronteira HTTP que vale para todos os recursos e a paginação padrão (20 por página, no máximo 100, ordenada por id).
- Requisitos: RF006, RF010, RF015.
- Caminhos tristes: campos obrigatórios e tamanhos do transfer, enum inválido (lista os aceitos), JSON malformado, PATCH só com espaços, tipo errado na URL, moeda inválida, login sem campos e tamanho de página absurdo.

**AutorizacaoWebTest** — 8 métodos / 42 execuções
- Cobre: a matriz de permissões rota a rota (o que cada perfil pode e não pode fazer), o login público, o CORS do front e os headers de segurança (`nosniff`, `X-Frame-Options: DENY`).
- Requisitos: HU09, RF011, RNF002.
- Caminhos tristes: sem token (401 no formato padrão), escrita na auditoria (405), preflight de origem desconhecida recusado e cada perfil numa rota proibida (403).

**TratamentoGlobalErrosWebTest** — 18 métodos / 18 execuções
- Cobre: os erros que não são de um recurso só e passam pelo `GlobalExceptionHandler`.
- Requisitos: HU01, RNF008.
- Caminhos tristes: falha inesperada (500 sem vazar detalhe interno), duas gravações simultâneas barradas pelo `@Version` (409), login inválido (401) ou bloqueado (429), login com campos grandes demais, troca de senha inválida, parâmetros do painel, da auditoria e da cotação com formato errado, ordenação por campo inexistente, número vindo como texto, verbo não suportado (405) e rota inexistente (404).

### 3.4 Repositório — SQL real no PostgreSQL (`repository/`)

**EspecificacoesTest** — 12 métodos / 16 execuções
- Cobre: as peças de filtro das listagens: filtro vazio não filtra, "contém" sem diferenciar maiúsculas e campo aninhado (`motorista.id`).
- Requisitos: RF015.
- Caminhos tristes: `%` e `_` digitados tratados como texto (sem virar curinga), período aberto em qualquer ponta, período invertido recusado.

**FiltrosNoBancoRepositoryTest** — 13 métodos / 13 execuções
- Cobre: os mesmos filtros executados no banco real: busca em mais de um campo, período fechado ou aberto, filtros somados e ordenação.
- Requisitos: RF015, HU07.
- Caminhos tristes: busca só com `%`, underline que não pode virar curinga, ordenação por campo inexistente, filtro "nenhum".

**ConsultasDoPainelRepositoryTest** — 6 métodos / 6 execuções
- Cobre: as consultas agregadas do painel: contagem por status num dia e faturamento de um período.
- Requisitos: HU08, RF012.
- Caminhos tristes: dia sem registros, transfer cancelado ou sem valor (fora da soma), período vazio (soma zero, e não null).

**PassageiroRepositoryTest** — 7 métodos / 7 execuções
- Cobre: os passageiros de um transfer em ordem de nome, a busca por nacionalidade e o documento gravado cifrado na coluna.
- Requisitos: HU02, RF007, RNF007.
- Caminhos tristes: transfer sem passageiros ou inexistente, nacionalidade inexistente.

**MotoristaRepositoryTest** — 7 métodos / 7 execuções
- Cobre: busca por CNH e unicidade da CNH no banco.
- Requisitos: HU03, RF003.
- Caminhos tristes: CNH duplicada recusada pelo banco, exclusão de motorista que tem OS barrada pela FK.

**VeiculoRepositoryTest** — 5 métodos / 5 execuções
- Cobre: busca por placa e placa única no banco.
- Requisitos: HU03, RF004.
- Caminhos tristes: placa inexistente, placa duplicada recusada pelo banco.

**UsuarioRepositoryTest** — 8 métodos / 8 execuções
- Cobre: busca por username e quem está ligado a um cadastro de motorista.
- Requisitos: HU09, HU07.
- Caminhos tristes: username duplicado e segundo usuário para o mesmo motorista, os dois recusados pelo banco.

**PontoColetaRepositoryTest** — 3 métodos / 3 execuções
- Cobre: os pontos de coleta de um transfer voltam em ordem de parada.
- Requisitos: HU05, RF008.
- Caminhos tristes: não traz pontos de outro transfer, transfer sem pontos.

**ParadaOsRepositoryTest** — 4 métodos / 4 execuções
- Cobre: as paradas de uma OS com os transfers na mesma consulta (sem N+1), o carregamento em lote e as paradas das OS de um motorista.
- Requisitos: HU07, RNF005.
- Caminhos tristes: motorista sem OS.

**LogAuditoriaRepositoryTest** — 3 métodos / 3 execuções
- Cobre: o histórico de auditoria filtrado por tabela e por registro.
- Requisitos: HU11, RF014.
- Caminhos tristes: outra tabela com o mesmo id (não mistura), registro sem histórico.

### 3.5 Integração — a aplicação inteira (`integracao/`)

**AutenticacaoIntegrationTest** — 11 métodos / 11 execuções
- Cobre: o fluxo de autenticação ponta a ponta: o admin do seed precisa trocar a senha, login, `/auth/me` e troca de senha.
- Requisitos: HU01, RF001, RNF001, RNF002.
- Caminhos tristes: token antigo recusado depois da troca de senha, usuário desativado perdendo o acesso na hora, perfil alterado valendo sem novo login, bloqueio por tentativas, escrita só com cookie e sem token (401, premissa do CSRF desligado, ADR-0015).

**CotacaoIntegrationTest** — 5 métodos / 5 execuções
- Cobre: a integração com a API de câmbio, simulada com WireMock. O cache faz uma chamada só para três consultas iguais.
- Requisitos: RF016.
- Caminhos tristes: API devolvendo 500 (502 para o cliente), API lenta (502 pelo timeout), transfer criado mesmo com a API fora do ar.

**FiltrosEMinhasOsIntegrationTest** — 19 métodos / 19 execuções
- Cobre: os filtros das listagens virando SQL de verdade e a regra "o motorista só vê as próprias OS".
- Requisitos: RF015, HU07, RF011.
- Caminhos tristes: período invertido (400), motorista buscando OS, PDF ou paradas de outro (404), gerente pedindo "minhas OS" (403), motorista sem cadastro ligado (lista vazia), ligar motorista a usuário de outro perfil (400).

**PersistenciaIntegrationTest** — 9 métodos / 9 execuções
- Cobre: o que só aparece com um banco de verdade: o documento cifrado no banco mas legível pela API, a paginação real com totais e o enum de status persistido.
- Requisitos: HU02, RNF007, RF015.
- Caminhos tristes: mesmo CPF com outra pontuação (400), motorista lendo passageiro (403), excluir motorista em uso (409 pela FK), ordenar por campo inexistente (400), transfer apontando OS inexistente (404).

**RelacionamentosIntegrationTest** — 26 métodos / 26 execuções
- Cobre: transfer com passageiros (tabela de junção), transfer dentro de uma OS, a trilha de auditoria, o PDF real, o controle de versão e o painel.
- Requisitos: HU04, HU06, HU08, HU10, HU11, RF007, RF009, RF013, RF014, RNF008.
- Caminhos tristes: passageiro inexistente (404), mudar o dia de OS com transfer, transfer em OS de outro dia ou cancelada (400), atendente na auditoria (403), criação que falha sem deixar log, versão velha (409), atendente no painel sem ver o faturamento.

**VersaoCadastrosIntegrationTest** — 6 métodos / 6 execuções
- Cobre: o controle de concorrência nos cadastros. A resposta traz a versão, e a edição com a versão certa passa e sobe a versão.
- Requisitos: RNF008.
- Caminhos tristes: versão velha (409, sem sobrescrever quem salvou antes), cliente antigo sem versão, login que não conta como edição do usuário.

### 3.6 Classes base (sem teste próprio)

- **IntegracaoTestBase**: sobe a aplicação com um PostgreSQL 16 real (Testcontainers, um container só para todas as classes) e a API de câmbio no WireMock, além de ajudar a logar como qualquer perfil.
- **RepositorioTestBase**: sobe só a camada JPA com o mesmo PostgreSQL. Cada teste roda numa transação desfeita no final.
- **WebTestBase / ErrosWebTestBase**: sobem o `@WebMvcTest` com os services mockados e as asserções do formato de erro (`erro(status, tipo, trecho)` e `validacao(trecho)`).

---

## 4. Convenções que valem para todos os testes

- **Nome:** `deveX_quandoY` ou `naoDeveX_quandoY`. O nome diz o resultado esperado e o cenário, então a lista de testes já é a especificação.
- **Corpo:** três blocos, `// given` (o cenário), `// when` (a ação) e `// then` (o que tem que ser verdade).
- **Cabeçalho da classe:** **o que testa** (os cenários), **como rodar** (o que precisa estar no ar) e **por que existe** (o problema real que o teste evita).
- **Caminho triste prova duas coisas:** o erro certo (status e corpo) **e** que nada foi gravado, alterado ou apagado (`verify(..., never())`).
- **Dados próprios por teste:** os testes de banco compartilham o mesmo container, então cada teste usa documentos, placas e usernames próprios. O banco recusa duplicados, inclusive de documento (V12).
