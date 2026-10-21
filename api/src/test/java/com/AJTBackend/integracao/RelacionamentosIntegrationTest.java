package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: os relacionamentos e a auditoria contra um postgres real —
 * transfer com passageiros (@ManyToMany, tabela transfer_passageiros),
 * transfer ligado a uma ordem de servico (@ManyToOne), passageiro
 * inexistente virando 404, o endpoint GET /transfers/{id}/passageiros, e a
 * trilha de auditoria (logs_transfers) sendo gravada e consultada so por
 * ADMIN/GERENTE. tambem prova que a migration V7 (bigint) casa com as
 * entidades (ddl-auto=validate).
 *
 * como rodar: precisa de docker (herda de IntegracaoTestBase). roda com
 * "mvn test"; sem docker, a classe inteira e pulada.
 *
 * por que existe: mapeamento jpa de tabela de juncao e tipo de coluna sao
 * coisas que so o banco de verdade valida — o teste unitario com mock nao
 * enxerga a tabela nem o schema criado pelo flyway.
 */
class RelacionamentosIntegrationTest extends IntegracaoTestBase {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long criarPassageiro(String nome, String documento) throws Exception {
        String json = mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\""
                                + documento + "\",\"nacionalidade\":\"Brasileira\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private long criarTransfer(String extra) throws Exception {
        String json = mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"GRU\","
                                + "\"destino\":\"Hotel\"" + extra + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void transferGuardaPassageirosNaTabelaDeJuncao() throws Exception {
        long ana = criarPassageiro("Ana", "AA111");
        long bia = criarPassageiro("Bia", "BB222");

        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "," + bia + "]");

        Integer vinculos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_passageiros WHERE transfer_id = ?", Integer.class, transferId);
        assertThat(vinculos).isEqualTo(2);

        mockMvc.perform(get("/api/transfers/" + transferId).header("Authorization", bearer(Role.MOTORISTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiroIds", hasSize(2)));

        mockMvc.perform(get("/api/transfers/" + transferId + "/passageiros")
                        .header("Authorization", bearer(Role.MOTORISTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nome").value("Ana"))   // ordenado por nome
                .andExpect(jsonPath("$[1].nome").value("Bia"))
                .andExpect(jsonPath("$[0].documento").value("AA111")); // volta decifrado
    }

    @Test
    void patchSubstituiEEnviarListaVaziaLimpaOsPassageiros() throws Exception {
        long ana = criarPassageiro("Ana", "AA333");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");

        mockMvc.perform(patch("/api/transfers/" + transferId).header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passageiroIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiroIds", hasSize(0)));

        mockMvc.perform(get("/api/transfers/" + transferId + "/passageiros")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void passageiroInexistenteRetorna404() throws Exception {
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"A\","
                                + "\"destino\":\"B\",\"passageiroIds\":[999999]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", containsString("999999")));
    }

    @Test
    void excluirPassageiroVinculadoRemoveSoOVinculo() throws Exception {
        long ana = criarPassageiro("Ana", "AA444");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");

        mockMvc.perform(delete("/api/passageiros/" + ana).header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transfers/" + transferId).header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiroIds", hasSize(0)));
    }

    @Test
    void transferLigadoAUmaOrdemDeServico() throws Exception {
        String os = mockMvc.perform(post("/api/ordens-servico").header("Authorization", bearer(Role.GERENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dataServico\":\"2026-09-25\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long osId = objectMapper.readTree(os).get("id").asLong();

        long transferId = criarTransfer(",\"osId\":" + osId);

        mockMvc.perform(get("/api/transfers/" + transferId).header("Authorization", bearer(Role.GERENTE)))
                .andExpect(jsonPath("$.osId").value(osId));

        Long fk = jdbcTemplate.queryForObject("SELECT os_id FROM transfers WHERE id = ?", Long.class, transferId);
        assertThat(fk).isEqualTo(osId);
    }

    @Test
    void auditoriaRegistraCriacaoEMudancaDeStatusEsoGerenteEAdminConsultam() throws Exception {
        long transferId = criarTransfer("");
        mockMvc.perform(patch("/api/transfers/" + transferId).header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMADO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auditoria").param("tabela", "transfers").param("size", "100")
                        .header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + transferId + ")].mensagem",
                        org.hamcrest.Matchers.hasItem(containsString("AGUARDANDO_OS -> CONFIRMADO"))))
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + transferId + ")].mensagem",
                        org.hamcrest.Matchers.hasItem(containsString("Transfer criado"))));

        mockMvc.perform(get("/api/auditoria").header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditoriaRegistraMudancaDeValorEFiltraPeloHistoricoDoTransfer() throws Exception {
        long transferId = criarTransfer(",\"valorBase\":150.00");
        long outroId = criarTransfer("");
        mockMvc.perform(patch("/api/transfers/" + transferId).header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valorBase\":180.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auditoria").param("tabela", "transfers").param("registroId", String.valueOf(transferId))
                        .header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].mensagem",
                        org.hamcrest.Matchers.hasItem(containsString("valor 150.00 -> 180.00"))))
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + outroId + ")]").isEmpty());
    }

    @Test
    void pdfDaOrdemDeServicoTrazPassageirosESemValores() throws Exception {
        String os = mockMvc.perform(post("/api/ordens-servico").header("Authorization", bearer(Role.GERENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dataServico\":\"2026-09-25\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long osId = objectMapper.readTree(os).get("id").asLong();
        long ana = criarPassageiro("Ana Relatorio", "PDF111");
        criarTransfer(",\"osId\":" + osId + ",\"valorBase\":321.00,\"passageiroIds\":[" + ana + "]");

        byte[] pdf = mockMvc.perform(get("/api/ordens-servico/" + osId + "/pdf")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("ordem-servico-" + osId + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        PdfReader leitor = new PdfReader(pdf);
        String texto = new PdfTextExtractor(leitor).getTextFromPage(1);
        leitor.close();
        assertThat(texto).contains("Ordem de Serviço nº " + osId, "Ana Relatorio", "GRU");
        assertThat(texto).doesNotContain("321", "PDF111"); // nem valor nem documento no papel do motorista

        mockMvc.perform(get("/api/ordens-servico/999999/pdf").header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void edicaoComVersaoVelhaRetorna409EPreservaAAlteracaoDoOutroUsuario() throws Exception {
        long transferId = criarTransfer("");
        Long versao = jdbcTemplate.queryForObject("SELECT versao FROM transfers WHERE id = ?", Long.class, transferId);

        // a gerente salva primeiro, em cima da versao que carregou
        mockMvc.perform(patch("/api/transfers/" + transferId).header("Authorization", bearer(Role.GERENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMADO\",\"versao\":" + versao + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(versao + 1));

        // a atendente tinha carregado a mesma versao e tenta salvar depois: 409, nada e sobrescrito
        mockMvc.perform(patch("/api/transfers/" + transferId).header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\",\"versao\":" + versao + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de edição"));

        mockMvc.perform(get("/api/transfers/" + transferId).header("Authorization", bearer(Role.GERENTE)))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));
    }

    @Test
    void painelContaODiaESomaOFaturamentoSemCancelados() throws Exception {
        // data so deste teste, pra nao misturar com os transfers dos outros
        String base = "{\"dataTransfer\":\"2031-01-15\",\"horaTransfer\":\"09:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\"";
        for (String extra : List.of(",\"valorBase\":100.00}", ",\"valorBase\":50.50}",
                ",\"valorBase\":999.00,\"status\":\"CANCELADO\"}")) {
            mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                            .contentType(MediaType.APPLICATION_JSON).content(base + extra))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/painel/resumo").param("data", "2031-01-15")
                        .header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfers").value(3))
                .andExpect(jsonPath("$.transfersPorStatus.AGUARDANDO_OS").value(2))
                .andExpect(jsonPath("$.transfersPorStatus.CANCELADO").value(1))
                .andExpect(jsonPath("$.faturamentoDia").value(150.50))
                .andExpect(jsonPath("$.faturamentoMes").value(150.50));

        mockMvc.perform(get("/api/painel/resumo").param("data", "2031-01-15")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfers").value(3))
                .andExpect(jsonPath("$.faturamentoDia").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void auditoriaNaoSobraQuandoAOperacaoFalha() throws Exception {
        Integer antes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM logs_transfers WHERE tabela_afetada = 'transfers'", Integer.class);

        // passageiro inexistente: a transacao de criacao aborta, entao nao pode sobrar log
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"A\","
                                + "\"destino\":\"B\",\"passageiroIds\":[888888]}"))
                .andExpect(status().isNotFound());

        Integer depois = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM logs_transfers WHERE tabela_afetada = 'transfers'", Integer.class);
        assertThat(depois).isEqualTo(antes);
    }
}
