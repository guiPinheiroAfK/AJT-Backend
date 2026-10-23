package com.AJTBackend.integracao;

import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: os filtros das listagens (RF015) e a regra "o motorista so ve as
 * proprias OS" (HU07) contra um postgres real — as Specifications viram SQL de
 * verdade (like sem diferenciar maiusculas, periodo, relacao com a OS) e o
 * vinculo usuario -> motorista da V11 decide o que o MOTORISTA enxerga.
 *
 * como rodar: herda IntegracaoTestBase (testcontainers); precisa do docker.
 * cada teste usa datas e textos so dele (ano 2032, nomes com UUID) pra nao
 * misturar com os registros dos outros testes no mesmo banco.
 *
 * por que existe: filtro errado nao quebra nada, so devolve a lista errada —
 * e motorista vendo OS de outro motorista e vazamento que ninguem percebe.
 */
class FiltrosEMinhasOsIntegrationTest extends IntegracaoTestBase {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private MotoristaRepository motoristaRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;
    @Autowired private ParadaOsRepository paradaOsRepository;

    private void criarTransfer(String data, String origem) throws Exception {
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"" + data + "\",\"horaTransfer\":\"10:00:00\",\"origem\":\"" + origem
                                + "\",\"destino\":\"Hotel\"}"))
                .andExpect(status().isCreated());
    }

    private Motorista motorista(String nome) {
        return motoristaRepository.save(Motorista.builder()
                .nome(nome).cnh(UUID.randomUUID().toString().substring(0, 11)).build());
    }

    private ResultActions listar(String url, Role perfil, String... parametros) throws Exception {
        var requisicao = get(url).header("Authorization", bearer(perfil));
        for (int i = 0; i < parametros.length; i += 2) {
            requisicao.param(parametros[i], parametros[i + 1]);
        }
        return mockMvc.perform(requisicao);
    }

    // ---------------- RF015: filtros das listagens ----------------

    @Test
    void transfersFiltramPorPeriodoIncluindoAsDuasPontas() throws Exception {
        // Given
        criarTransfer("2032-03-01", "Rodoviaria");
        criarTransfer("2032-03-02", "Centro");
        criarTransfer("2032-03-03", "Aeroporto");

        // When
        ResultActions resposta = listar("/api/transfers", Role.ATENDENTE, "dataInicio", "2032-03-02", "dataFim", "2032-03-03");

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].origem", containsInAnyOrder("Centro", "Aeroporto")));
    }

    @Test
    void transfersFiltramPorParteDoTextoSemDiferenciarMaiusculas() throws Exception {
        // Given
        String marca = "Marina-" + UUID.randomUUID().toString().substring(0, 6);
        criarTransfer("2032-03-10", marca);
        criarTransfer("2032-03-10", "Rodoviaria");

        // When
        ResultActions resposta = listar("/api/transfers", Role.ATENDENTE,
                "busca", marca.toUpperCase().substring(0, 9), "dataInicio", "2032-03-10");

        // Then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].origem").value(marca));
    }

    @Test
    void filtrosDeTransferSeSomam() throws Exception {
        // Given: tres transfers sem OS no periodo, todos aguardando OS
        criarTransfer("2032-05-01", "A");
        criarTransfer("2032-05-02", "B");
        criarTransfer("2032-05-03", "C");

        // When
        ResultActions resposta = listar("/api/transfers", Role.ATENDENTE, "dataInicio", "2032-05-01", "dataFim", "2032-05-03",
                "semOs", "true", "status", "AGUARDANDO_OS");

        // Then
        resposta.andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.conteudo[*].osId", everyItem(nullValue())));
    }

    @Test
    void periodoInvertidoNaListagemRetorna400() throws Exception {
        // When
        ResultActions resposta = listar("/api/transfers", Role.ATENDENTE, "dataInicio", "2032-03-05", "dataFim", "2032-03-01");

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("depois da final")));
    }

    @Test
    void passageirosFiltramPorParteDoNome() throws Exception {
        // Given
        String sobrenome = "Zq" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana " + sobrenome + "\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X1\","
                                + "\"nacionalidade\":\"Argentina\"}"))
                .andExpect(status().isCreated());

        // When
        ResultActions resposta = listar("/api/passageiros", Role.ATENDENTE, "nome", sobrenome.toLowerCase());

        // Then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nacionalidade").value("Argentina"));
    }

    @Test
    void motoristasFiltramPorParteDoNome() throws Exception {
        // Given
        String sobrenome = "Zq" + UUID.randomUUID().toString().substring(0, 6);
        motorista("Carlos " + sobrenome);

        // When
        ResultActions resposta = listar("/api/motoristas", Role.GERENTE, "nome", sobrenome);

        // Then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Carlos " + sobrenome));
    }

    @Test
    void ordensDeServicoFiltramPorMotorista() throws Exception {
        // Given
        Motorista carlos = motorista("Carlos");
        Motorista dora = motorista("Dora");
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 2)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(dora).build());

        // When
        ResultActions resposta = listar("/api/ordens-servico", Role.GERENTE, "motoristaId", String.valueOf(carlos.getId()));

        // Then
        resposta.andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].motoristaId", everyItem(is(carlos.getId().intValue()))));
    }

    @Test
    void ordensDeServicoFiltramPorMotoristaEPeriodoJuntos() throws Exception {
        // Given
        Motorista carlos = motorista("Carlos");
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 2)).motorista(carlos).build());

        // When
        ResultActions resposta = listar("/api/ordens-servico", Role.GERENTE,
                "motoristaId", String.valueOf(carlos.getId()), "dataInicio", "2032-04-02");

        // Then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].dataServico").value("2032-04-02"));
    }

    // ---------------- HU07: o motorista so enxerga as proprias OS ----------------

    private record Cenario(String tokenMotorista, OrdemServico minha, OrdemServico deOutro) {}

    // dois motoristas com OS hoje; o usuario "eu" e ligado ao cadastro pelo admin, via api
    private Cenario motoristaLigadoComOsDeHoje() throws Exception {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Motorista eu = motorista("Eu Motorista");
        Motorista outro = motorista("Outro Motorista");
        OrdemServico minha = ordemServicoRepository.save(OrdemServico.builder().dataServico(hoje).motorista(eu).build());
        OrdemServico deOutro = ordemServicoRepository.save(OrdemServico.builder().dataServico(hoje).motorista(outro).build());
        paradaOsRepository.save(ParadaOs.builder().ordemServico(minha).ordemParada(1).localParada("Aeroporto").build());

        String json = mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Eu\",\"username\":\"mot-" + UUID.randomUUID().toString().substring(0, 8)
                                + "\",\"senha\":\"senha12345\",\"role\":\"MOTORISTA\",\"motoristaId\":" + eu.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String username = objectMapper.readTree(json).get("username").asText();
        return new Cenario("Bearer " + jwtService.gerarToken(username, Role.MOTORISTA), minha, deOutro);
    }

    @Test
    void motoristaListaSoAsPropriasOs() throws Exception {
        // Given
        Cenario cenario = motoristaLigadoComOsDeHoje();

        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico").param("size", "100")
                .header("Authorization", cenario.tokenMotorista()));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].id", hasItem(cenario.minha().getId().intValue())))
                .andExpect(jsonPath("$.conteudo[*].id", not(hasItem(cenario.deOutro().getId().intValue()))));
    }

    @ParameterizedTest(name = "GET {0} da OS de outro motorista -> 404")
    @ValueSource(strings = {"/api/ordens-servico/%d", "/api/ordens-servico/%d/pdf", "/api/paradas-os/ordem-servico/%d"})
    void osDeOutroMotoristaNaoExisteParaEle(String rota) throws Exception {
        // Given
        Cenario cenario = motoristaLigadoComOsDeHoje();

        // When
        ResultActions resposta = mockMvc.perform(get(String.format(rota, cenario.deOutro().getId()))
                .header("Authorization", cenario.tokenMotorista()));

        // Then
        resposta.andExpect(status().isNotFound());
    }

    @Test
    void minhasDoDiaTrazAOsDeHojeComAsParadas() throws Exception {
        // Given
        Cenario cenario = motoristaLigadoComOsDeHoje();

        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/minhas").header("Authorization", cenario.tokenMotorista()));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ordem.id").value(cenario.minha().getId()))
                .andExpect(jsonPath("$[0].paradas", hasSize(1)))
                .andExpect(jsonPath("$[0].paradas[0].localParada").value("Aeroporto"));
    }

    @Test
    void gerenteVeAOsDeQualquerMotorista() throws Exception {
        // Given
        Cenario cenario = motoristaLigadoComOsDeHoje();

        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/" + cenario.deOutro().getId())
                .header("Authorization", bearer(Role.GERENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cenario.deOutro().getId()));
    }

    @Test
    void minhasDoDiaEhExclusivaDoMotorista() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/minhas").header("Authorization", bearer(Role.GERENTE)));

        // Then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void motoristaSemCadastroLigadoListaNenhumaOs() throws Exception {
        // Given
        Usuario semVinculo = criarUsuario(Role.MOTORISTA, "senha-teste-123");
        String token = "Bearer " + jwtService.gerarToken(semVinculo.getUsername(), Role.MOTORISTA);

        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico").header("Authorization", token));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void motoristaSemCadastroLigadoNaoTemOsDoDia() throws Exception {
        // Given
        Usuario semVinculo = criarUsuario(Role.MOTORISTA, "senha-teste-123");
        String token = "Bearer " + jwtService.gerarToken(semVinculo.getUsername(), Role.MOTORISTA);

        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/minhas").header("Authorization", token));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void naoLigaMotoristaAUsuarioDeOutroPerfil() throws Exception {
        // Given
        Motorista carlos = motorista("Carlos");
        String atendenteComMotorista = "{\"nome\":\"Ana\",\"username\":\"ana-" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\",\"motoristaId\":" + carlos.getId() + "}";

        // When
        ResultActions resposta = mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(atendenteComMotorista));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("perfil Motorista")));
    }
}
