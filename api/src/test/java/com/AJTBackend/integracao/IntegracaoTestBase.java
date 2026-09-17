package com.AJTBackend.integracao;

import com.AJTBackend.config.JwtService;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.UsuarioRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/*
 * classe base dos testes de integracao (AutenticacaoIntegrationTest,
 * PersistenciaIntegrationTest, PerformanceIntegrationTest,
 * CotacaoIntegrationTest). sobe a aplicacao inteira, de verdade, contra um
 * PostgreSQL 16 real via testcontainers — rodando todas as migrations do
 * flyway (V1 a V6) com ddl-auto=validate — e simula a api de cambio com
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

    /** Cria um usuario ativo com o perfil informado e devolve o header Authorization. */
    protected String bearer(Role role) {
        return "Bearer " + jwtService.gerarToken(criarUsuario(role, "senha-teste-123").getUsername(), role);
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
