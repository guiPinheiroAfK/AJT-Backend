package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

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

    private ResultActions enviarTransfer(String extra) throws Exception {
        return mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"GRU\","
                        + "\"destino\":\"Hotel\"" + extra + "}"));
    }

    private long criarTransfer(String extra) throws Exception {
        String json = enviarTransfer(extra)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private long criarOs(String corpo) throws Exception {
        String json = mockMvc.perform(post("/api/ordens-servico").header("Authorization", bearer(Role.GERENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    // cada teste passa documentos proprios: o banco e compartilhado e documento repetido e recusado (V12)
    private long transferComAnaEBia(String documentoAna, String documentoBia) throws Exception {
        long ana = criarPassageiro("Ana", documentoAna);
        long bia = criarPassageiro("Bia", documentoBia);
        return criarTransfer(",\"passageiroIds\":[" + bia + "," + ana + "]");
    }

    private ResultActions alterarTransfer(long transferId, Role perfil, String corpo) throws Exception {
        return mockMvc.perform(patch("/api/transfers/" + transferId).header("Authorization", bearer(perfil))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private int vinculosDoTransfer(long transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_passageiros WHERE transfer_id = ?", Integer.class, transferId);
    }

    private Long osDoTransferNoBanco(long transferId) {
        return jdbcTemplate.queryForObject("SELECT os_id FROM transfers WHERE id = ?", Long.class, transferId);
    }

    private String statusDoTransferNoBanco(long transferId) {
        return jdbcTemplate.queryForObject("SELECT status FROM transfers WHERE id = ?", String.class, transferId);
    }

    private int logsDeTransfers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM logs_transfers WHERE tabela_afetada = 'transfers'", Integer.class);
    }

    @Test
    void transferGuardaPassageirosNaTabelaDeJuncao() throws Exception {
        // Given
        long ana = criarPassageiro("Ana", "AA777");
        long bia = criarPassageiro("Bia", "BB888");

        // When
        ResultActions resposta = enviarTransfer(",\"passageiroIds\":[" + ana + "," + bia + "]");

        // Then
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.passageiroIds", hasSize(2)));
        long transferId = objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(vinculosDoTransfer(transferId)).isEqualTo(2);
    }

    @Test
    void motoristaVePassageirosDoTransferPorNomeEComDocumentoMascarado() throws Exception {
        // Given
        long transferId = transferComAnaEBia("AA111", "BB222");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers/" + transferId + "/passageiros")
                .header("Authorization", bearer(Role.MOTORISTA)));

        // Then: ordenado por nome, e o motorista ve so o final do documento
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nome").value("Ana"))
                .andExpect(jsonPath("$[1].nome").value("Bia"))
                .andExpect(jsonPath("$[0].documento").value("***11"));
    }

    @Test
    void atendenteVeODocumentoDosPassageirosDoTransferDecifrado() throws Exception {
        // Given
        long transferId = transferComAnaEBia("AA555", "BB666");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers/" + transferId + "/passageiros")
                .header("Authorization", bearer(Role.ATENDENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documento").value("AA555"))
                .andExpect(jsonPath("$[1].documento").value("BB666"));
    }

    @Test
    void patchComListaVaziaLimpaOsPassageiros() throws Exception {
        // Given
        long ana = criarPassageiro("Ana", "AA333");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");

        // When
        ResultActions resposta = alterarTransfer(transferId, Role.ATENDENTE, "{\"passageiroIds\":[]}");

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiroIds", hasSize(0)));
        assertThat(vinculosDoTransfer(transferId)).isZero();
    }

    @Test
    void passageiroInexistenteRetorna404() throws Exception {
        // When
        ResultActions resposta = enviarTransfer(",\"passageiroIds\":[999999]");

        // Then
        resposta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", containsString("999999")));
    }

    @Test
    void excluirPassageiroVinculadoRemoveSoOVinculo() throws Exception {
        // Given
        long ana = criarPassageiro("Ana", "AA444");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");

        // When
        ResultActions resposta = mockMvc.perform(delete("/api/passageiros/" + ana)
                .header("Authorization", bearer(Role.GERENTE)));

        // Then: o transfer continua existindo, so sem o passageiro
        resposta.andExpect(status().isNoContent());
        assertThat(vinculosDoTransfer(transferId)).isZero();
        assertThat(statusDoTransferNoBanco(transferId)).isEqualTo("AGUARDANDO_OS");
    }

    @Test
    void transferLigadoAUmaOrdemDeServicoJaNasceConfirmado() throws Exception {
        // Given
        long osId = criarOs("{\"dataServico\":\"2026-09-25\"}");

        // When
        ResultActions resposta = enviarTransfer(",\"osId\":" + osId);

        // Then: com OS nao fica "aguardando OS", e a chave estrangeira vai pro banco
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.osId").value(osId))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));
        long transferId = objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(osDoTransferNoBanco(transferId)).isEqualTo(osId);
    }

    @Test
    void ordemDeServicoComTransferDentroNaoMudaDeDia() throws Exception {
        // Given: os dois sairiam de sincronia
        long osId = criarOs("{\"dataServico\":\"2026-09-25\"}");
        criarTransfer(",\"osId\":" + osId);

        // When
        ResultActions resposta = mockMvc.perform(patch("/api/ordens-servico/" + osId)
                .header("Authorization", bearer(Role.GERENTE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dataServico\":\"2026-09-26\"}"));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("Desvincule")));
    }

    @Test
    void transferNaoEntraEmOsDeOutroDia() throws Exception {
        // Given: transfer do dia 25 e OS do dia 26
        long osOutroDia = criarOs("{\"dataServico\":\"2026-09-26\"}");
        long transferId = criarTransfer("");

        // When
        ResultActions resposta = alterarTransfer(transferId, Role.ATENDENTE, "{\"osId\":" + osOutroDia + "}");

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("mesmo dia")));
        assertThat(osDoTransferNoBanco(transferId)).isNull();
    }

    @Test
    void transferNaoEntraEmOsCancelada() throws Exception {
        // Given
        long osCancelada = criarOs("{\"dataServico\":\"2026-09-25\",\"status\":\"CANCELADA\"}");
        long transferId = criarTransfer("");

        // When
        ResultActions resposta = alterarTransfer(transferId, Role.ATENDENTE, "{\"osId\":" + osCancelada + "}");

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("CANCELADA")));
        assertThat(osDoTransferNoBanco(transferId)).isNull();
        assertThat(statusDoTransferNoBanco(transferId)).isEqualTo("AGUARDANDO_OS");
    }

    @Test
    void auditoriaRegistraCriacaoEMudancaDeStatusDoTransfer() throws Exception {
        // Given
        long transferId = criarTransfer("");
        alterarTransfer(transferId, Role.ATENDENTE, "{\"status\":\"CONFIRMADO\"}").andExpect(status().isOk());

        // When
        ResultActions resposta = mockMvc.perform(get("/api/auditoria").param("tabela", "transfers").param("size", "100")
                .header("Authorization", bearer(Role.GERENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + transferId + ")].mensagem",
                        org.hamcrest.Matchers.hasItem(containsString("AGUARDANDO_OS -> CONFIRMADO"))))
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + transferId + ")].mensagem",
                        org.hamcrest.Matchers.hasItem(containsString("Transfer criado"))));
    }

    @Test
    void atendenteNaoConsultaAAuditoria() throws Exception {
        // When: so gerente e admin consultam
        ResultActions resposta = mockMvc.perform(get("/api/auditoria").header("Authorization", bearer(Role.ATENDENTE)));

        // Then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void auditoriaRegistraMudancaDeValorEFiltraPeloHistoricoDoTransfer() throws Exception {
        // Given
        long transferId = criarTransfer(",\"valorBase\":150.00");
        long outroId = criarTransfer("");
        alterarTransfer(transferId, Role.ATENDENTE, "{\"valorBase\":180.00}").andExpect(status().isOk());

        // When
        ResultActions resposta = mockMvc.perform(get("/api/auditoria").param("tabela", "transfers")
                .param("registroId", String.valueOf(transferId))
                .header("Authorization", bearer(Role.GERENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].mensagem",
                        org.hamcrest.Matchers.hasItem(containsString("valor 150.00 -> 180.00"))))
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + outroId + ")]").isEmpty());
    }

    @Test
    void pdfDaOrdemDeServicoTrazPassageirosESemValores() throws Exception {
        // Given
        long osId = criarOs("{\"dataServico\":\"2026-09-25\"}");
        long ana = criarPassageiro("Ana Relatorio", "PDF111");
        criarTransfer(",\"osId\":" + osId + ",\"valorBase\":321.00,\"passageiroIds\":[" + ana + "]");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/" + osId + "/pdf")
                .header("Authorization", bearer(Role.ATENDENTE)));

        // Then: nem valor nem documento no papel do motorista
        resposta.andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("ordem-servico-" + osId + ".pdf")));
        String texto;
        try (PdfReader leitor = new PdfReader(resposta.andReturn().getResponse().getContentAsByteArray())) {
            texto = new PdfTextExtractor(leitor).getTextFromPage(1);
        }
        assertThat(texto).contains("Ordem de Serviço nº " + osId, "Ana Relatorio", "GRU")
                .doesNotContain("321", "PDF111");
    }

    @Test
    void pdfDeOrdemDeServicoInexistenteRetorna404() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/999999/pdf")
                .header("Authorization", bearer(Role.ATENDENTE)));

        // Then
        resposta.andExpect(status().isNotFound());
    }

    @Test
    void edicaoComVersaoVelhaRetorna409EPreservaAAlteracaoDoOutroUsuario() throws Exception {
        // Given: a gerente e a atendente carregam a mesma versao, e a gerente salva primeiro
        long transferId = criarTransfer("");
        Long versao = jdbcTemplate.queryForObject("SELECT versao FROM transfers WHERE id = ?", Long.class, transferId);
        alterarTransfer(transferId, Role.GERENTE, "{\"status\":\"CONFIRMADO\",\"versao\":" + versao + "}")
                .andExpect(jsonPath("$.versao").value(versao + 1));

        // When: a atendente salva depois, em cima da versao que ja ficou velha
        ResultActions resposta = alterarTransfer(transferId, Role.ATENDENTE,
                "{\"status\":\"CANCELADO\",\"versao\":" + versao + "}");

        // Then: nada e sobrescrito
        resposta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de edição"));
        assertThat(statusDoTransferNoBanco(transferId)).isEqualTo("CONFIRMADO");
    }

    private void criarTransfersDoPainel(String data) throws Exception {
        // data so do teste, pra nao misturar com os transfers dos outros
        String base = "{\"dataTransfer\":\"" + data + "\",\"horaTransfer\":\"09:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\"";
        for (String extra : List.of(",\"valorBase\":100.00}", ",\"valorBase\":50.50}",
                ",\"valorBase\":999.00,\"status\":\"CANCELADO\"}")) {
            mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                            .contentType(MediaType.APPLICATION_JSON).content(base + extra))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void painelContaODiaESomaOFaturamentoSemCancelados() throws Exception {
        // Given
        criarTransfersDoPainel("2031-01-15");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/painel/resumo").param("data", "2031-01-15")
                .header("Authorization", bearer(Role.GERENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfers").value(3))
                .andExpect(jsonPath("$.transfersPorStatus.AGUARDANDO_OS").value(2))
                .andExpect(jsonPath("$.transfersPorStatus.CANCELADO").value(1))
                .andExpect(jsonPath("$.faturamentoDia").value(150.50))
                .andExpect(jsonPath("$.faturamentoMes").value(150.50));
    }

    @Test
    void atendenteVeOPainelSemOFaturamento() throws Exception {
        // Given
        criarTransfersDoPainel("2031-02-15");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/painel/resumo").param("data", "2031-02-15")
                .header("Authorization", bearer(Role.ATENDENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfers").value(3))
                .andExpect(jsonPath("$.faturamentoDia").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void auditoriaNaoSobraQuandoAOperacaoFalha() throws Exception {
        // Given
        int antes = logsDeTransfers();

        // When: passageiro inexistente, a transacao de criacao aborta
        ResultActions resposta = enviarTransfer(",\"passageiroIds\":[888888]");

        // Then: entao nao pode sobrar log
        resposta.andExpect(status().isNotFound());
        assertThat(logsDeTransfers()).isEqualTo(antes);
    }
}
