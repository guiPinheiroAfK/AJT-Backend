package com.ajtbackend.integracao;

import com.ajtbackend.config.JwtService;
import com.ajtbackend.model.Usuario;
import com.ajtbackend.model.enums.Role;
import com.ajtbackend.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * classe base dos testes de integracao (todas as classes de integracao/).
 * sobe a aplicacao inteira, de verdade, contra um
 * PostgreSQL 16 real via testcontainers — rodando todas as migrations do
 * flyway (V1 a V11) com ddl-auto=validate — e simula a api de cambio com
 * wiremock, sem sair pra internet.
 *
 * como rodar: precisa do docker desktop (ou outro engine docker) rodando
 * na maquina. com "mvn test", se o docker nao for encontrado, as classes
 * que herdam daqui sao PULADAS automaticamente (@Testcontainers
 * (disabledWithoutDocker = true)) em vez de falhar o build.
 *
 * por que existe: os testes unitarios (com mock) provam que a logica esta
 * certa, mas nao pegam problema de sql, de mapeamento jpa/flyway ou de
 * configuracao real (timeout do feign, cache, cors) — so um banco e uma
 * aplicacao de verdade sobem esses problemas. os metodos "bearer"/
 * "criarUsuario" abaixo existem pra cada teste conseguir logar como
 * qualquer perfil sem repetir esse setup em toda classe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
abstract class IntegracaoTestBase {

    /*
     * padrao "singleton container" do testcontainers: os dois campos abaixo
     * sao static e ficam numa classe base herdada por varias classes de
     * teste, entao subem UMA UNICA VEZ pra toda a suite (nao um por classe).
     *
     * de proposito SEM a anotacao @Container/@RegisterExtension: essas
     * anotacoes fazem o junit parar o recurso no afterAll de CADA classe que
     * herda daqui e reiniciar no beforeAll da proxima — como e o mesmo campo
     * estatico compartilhado entre as classes, isso reinicia postgres/wiremock
     * numa porta nova e quebra o contexto do spring das classes seguintes
     * (erro "CannotCreateTransaction" tentando falar com a porta antiga).
     * subindo no static{} manualmente, o recurso nasce uma vez e so morre
     * quando a jvm termina.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final WireMockServer COTACAO_API = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        POSTGRES.start();
        COTACAO_API.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("ajt.cotacao.url", COTACAO_API::baseUrl);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected ObjectMapper objectMapper;

    // cria um usuario ativo com o perfil informado e devolve o header Authorization
    protected String bearer(Role role) {
        return "Bearer " + jwtService.gerarToken(criarUsuario(role, "senha-teste-123").getUsername(), role);
    }

    /*
     * atalhos pro "when" dos testes: cada um dispara a requisicao com o header
     * Authorization e devolve o ResultActions, pra o "then" fazer os andExpect.
     * o "authorization" e o header inteiro: bearer(Role.X) ou "Bearer " + token.
     */

    protected ResultActions executar(MockHttpServletRequestBuilder requisicao, String authorization) throws Exception {
        return mockMvc.perform(requisicao.header("Authorization", authorization));
    }

    protected ResultActions getComo(String url, String authorization) throws Exception {
        return executar(get(url), authorization);
    }

    protected ResultActions deleteComo(String url, String authorization) throws Exception {
        return executar(delete(url), authorization);
    }

    protected ResultActions postJson(String url, String authorization, String json) throws Exception {
        return executar(post(url).contentType(MediaType.APPLICATION_JSON).content(json), authorization);
    }

    protected ResultActions putJson(String url, String authorization, String json) throws Exception {
        return executar(put(url).contentType(MediaType.APPLICATION_JSON).content(json), authorization);
    }

    protected ResultActions patchJson(String url, String authorization, String json) throws Exception {
        return executar(patch(url).contentType(MediaType.APPLICATION_JSON).content(json), authorization);
    }

    // le o "id" do corpo json da resposta
    protected long idDe(ResultActions resposta) throws Exception {
        return corpoDe(resposta).get("id").asLong();
    }

    protected JsonNode corpoDe(ResultActions resposta) throws Exception {
        return objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString());
    }

    // preparo pela api: cria o registro e devolve o id. confere o 201 aqui pra, se o
    // preparo quebrar, o teste falhar no lugar certo e nao numa assercao la na frente
    protected long criarPelaApi(String url, String authorization, String json) throws Exception {
        ResultActions resposta = postJson(url, authorization, json);
        resposta.andExpect(status().isCreated());
        return idDe(resposta);
    }

    protected Usuario criarUsuario(Role role, String senha) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuario " + role)
                .username(role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
                .senha(passwordEncoder.encode(senha))
                .role(role)
                .build());
    }
}
