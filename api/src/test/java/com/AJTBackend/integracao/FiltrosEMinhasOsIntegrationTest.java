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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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

    // ---------------- RF015 ----------------

    @Test
    void transfersFiltramPorPeriodoEPorTextoDaOrigemOuDestino() throws Exception {
        String marca = "Marina-" + UUID.randomUUID().toString().substring(0, 6);
        criarTransfer("2032-03-01", "Rodoviaria");
        criarTransfer("2032-03-02", marca);
        criarTransfer("2032-03-03", "Aeroporto");

        mockMvc.perform(get("/api/transfers").param("dataInicio", "2032-03-02").param("dataFim", "2032-03-03")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));

        // sem diferenciar maiusculas, por parte do texto
        mockMvc.perform(get("/api/transfers").param("busca", marca.toUpperCase().substring(0, 9))
                        .param("dataInicio", "2032-03-01").header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].origem").value(marca));

        mockMvc.perform(get("/api/transfers").param("dataInicio", "2032-03-01").param("dataFim", "2032-03-03")
                        .param("semOs", "true").param("status", "AGUARDANDO_OS")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(jsonPath("$.totalElementos").value(3));

        mockMvc.perform(get("/api/transfers").param("dataInicio", "2032-03-05").param("dataFim", "2032-03-01")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("depois da final")));
    }

    @Test
    void passageirosEMotoristasFiltramPorParteDoNome() throws Exception {
        String sobrenome = "Zq" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana " + sobrenome + "\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X1\","
                                + "\"nacionalidade\":\"Argentina\"}"))
                .andExpect(status().isCreated());
        motorista("Carlos " + sobrenome);

        mockMvc.perform(get("/api/passageiros").param("nome", sobrenome.toLowerCase())
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nacionalidade").value("Argentina"));

        mockMvc.perform(get("/api/motoristas").param("nome", sobrenome).header("Authorization", bearer(Role.GERENTE)))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Carlos " + sobrenome));
    }

    @Test
    void ordensDeServicoFiltramPorMotoristaEPeriodo() throws Exception {
        Motorista carlos = motorista("Carlos");
        Motorista dora = motorista("Dora");
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 2)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(dora).build());

        mockMvc.perform(get("/api/ordens-servico").param("motoristaId", String.valueOf(carlos.getId()))
                        .header("Authorization", bearer(Role.GERENTE)))
                .andExpect(jsonPath("$.totalElementos").value(2));

        mockMvc.perform(get("/api/ordens-servico").param("motoristaId", String.valueOf(carlos.getId()))
                        .param("dataInicio", "2032-04-02").header("Authorization", bearer(Role.GERENTE)))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    // ---------------- HU07 ----------------

    @Test
    void motoristaSoVeAsPropriasOsETemAsDeHojeComParadas() throws Exception {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Motorista eu = motorista("Eu Motorista");
        Motorista outro = motorista("Outro Motorista");
        OrdemServico minha = ordemServicoRepository.save(OrdemServico.builder().dataServico(hoje).motorista(eu).build());
        OrdemServico deOutro = ordemServicoRepository.save(OrdemServico.builder().dataServico(hoje).motorista(outro).build());
        paradaOsRepository.save(ParadaOs.builder().ordemServico(minha).ordemParada(1).localParada("Aeroporto").build());

        // o admin liga o usuario ao cadastro de motorista pela api
        String json = mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Eu\",\"username\":\"mot-" + UUID.randomUUID().toString().substring(0, 8)
                                + "\",\"senha\":\"senha12345\",\"role\":\"MOTORISTA\",\"motoristaId\":" + eu.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motoristaId").value(eu.getId()))
                .andReturn().getResponse().getContentAsString();
        String username = objectMapper.readTree(json).get("username").asText();
        String tokenMotorista = "Bearer " + jwtService.gerarToken(username, Role.MOTORISTA);

        mockMvc.perform(get("/api/ordens-servico").param("size", "100").header("Authorization", tokenMotorista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].motoristaId", everyItem(is(eu.getId().intValue()))));

        // a OS do outro motorista "nao existe" pra ele, nem no pdf nem nas paradas
        mockMvc.perform(get("/api/ordens-servico/" + deOutro.getId()).header("Authorization", tokenMotorista))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ordens-servico/" + deOutro.getId() + "/pdf").header("Authorization", tokenMotorista))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/paradas-os/ordem-servico/" + deOutro.getId()).header("Authorization", tokenMotorista))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/ordens-servico/minhas").header("Authorization", tokenMotorista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ordem.id").value(minha.getId()))
                .andExpect(jsonPath("$[0].paradas", hasSize(1)))
                .andExpect(jsonPath("$[0].paradas[0].localParada").value("Aeroporto"));

        // gerente ve as duas; "minhas" e so do motorista
        mockMvc.perform(get("/api/ordens-servico/" + deOutro.getId()).header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ordens-servico/minhas").header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void motoristaSemCadastroLigadoNaoVeNenhumaOs() throws Exception {
        Usuario semVinculo = criarUsuario(Role.MOTORISTA, "senha-teste-123");
        String token = "Bearer " + jwtService.gerarToken(semVinculo.getUsername(), Role.MOTORISTA);

        mockMvc.perform(get("/api/ordens-servico").header("Authorization", token))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(get("/api/ordens-servico/minhas").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void naoLigaMotoristaAUsuarioDeOutroPerfil() throws Exception {
        Motorista carlos = motorista("Carlos");

        mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"username\":\"ana-" + UUID.randomUUID().toString().substring(0, 8)
                                + "\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\",\"motoristaId\":" + carlos.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("perfil Motorista")));
    }
}
