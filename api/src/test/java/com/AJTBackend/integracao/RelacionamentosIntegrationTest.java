package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static final String TRANSFER_DIA_25 = "{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"GRU\","
            + "\"destino\":\"Hotel\"";

    @Autowired private JdbcTemplate jdbcTemplate;

    private long criarPassageiro(String nome, String documento) throws Exception {
        return criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE),
                "{\"nome\":\"" + nome + "\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\""
                        + documento + "\",\"nacionalidade\":\"Brasileira\"}");
    }

    private long criarTransfer(String extra) throws Exception {
        return criarPelaApi("/api/transfers", bearer(Role.ATENDENTE), TRANSFER_DIA_25 + extra + "}");
    }

    private long criarOs(String corpo) throws Exception {
        return criarPelaApi("/api/ordens-servico", bearer(Role.GERENTE), corpo);
    }

    // transfer com a Ana e a Bia (documentos AA111 e BB222)
    private long transferComAnaEBia() throws Exception {
        long ana = criarPassageiro("Ana", "AA111");
        long bia = criarPassageiro("Bia", "BB222");
        return criarTransfer(",\"passageiroIds\":[" + ana + "," + bia + "]");
    }

    private Integer vinculosDoTransfer(long transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_passageiros WHERE transfer_id = ?", Integer.class, transferId);
    }

    // ----- passageiros do transfer -----

    @Test
    void deveGravarNaTabelaDeJuncao_quandoTransferEhCriadoComPassageiros() throws Exception {
        // given
        long ana = criarPassageiro("Ana", "AA111");
        long bia = criarPassageiro("Bia", "BB222");

        // when
        ResultActions resposta = postJson("/api/transfers", bearer(Role.ATENDENTE),
                TRANSFER_DIA_25 + ",\"passageiroIds\":[" + ana + "," + bia + "]}");

        // then
        resposta.andExpect(status().isCreated());
        assertThat(vinculosDoTransfer(idDe(resposta))).isEqualTo(2);
    }

    @Test
    void deveDevolverOsIdsDosPassageiros_quandoMotoristaBuscaOTransfer() throws Exception {
        // given
        long transferId = transferComAnaEBia();

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId, bearer(Role.MOTORISTA));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiroIds", hasSize(2)));
    }

    @Test
    void deveListarPorNomeComDocumentoMascarado_quandoMotoristaListaPassageirosDoTransfer() throws Exception {
        // given
        long transferId = transferComAnaEBia();

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId + "/passageiros", bearer(Role.MOTORISTA));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nome").value("Ana"))   // ordenado por nome
                .andExpect(jsonPath("$[1].nome").value("Bia"))
                .andExpect(jsonPath("$[0].documento").value("***11")); // motorista ve so o final
    }

    @Test
    void deveMostrarODocumentoDecifrado_quandoAtendenteListaPassageirosDoTransfer() throws Exception {
        // given
        long transferId = transferComAnaEBia();

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId + "/passageiros", bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documento").value("AA111")) // atendente ve decifrado
                .andExpect(jsonPath("$[1].documento").value("BB222"));
    }

    @Test
    void deveLimparOsPassageiros_quandoPatchMandaListaVazia() throws Exception {
        // given
        long ana = criarPassageiro("Ana", "AA333");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");

        // when
        ResultActions resposta = patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"passageiroIds\":[]}");

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiroIds", hasSize(0)));
        assertThat(vinculosDoTransfer(transferId)).isZero();
    }

    @Test
    void deveListarNenhumPassageiro_quandoListaFoiLimpaPeloPatch() throws Exception {
        // given
        long ana = criarPassageiro("Ana", "AA333");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");
        patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"passageiroIds\":[]}")
                .andExpect(status().isOk());

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId + "/passageiros", bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deveRetornar404ComOId_quandoTransferApontaPassageiroInexistente() throws Exception {
        // when
        ResultActions resposta = postJson("/api/transfers", bearer(Role.ATENDENTE),
                "{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"A\","
                        + "\"destino\":\"B\",\"passageiroIds\":[999999]}");

        // then
        resposta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", containsString("999999")));
    }

    @Test
    void deveRemoverSoOVinculo_quandoExcluiPassageiroLigadoATransfer() throws Exception {
        // given
        long ana = criarPassageiro("Ana", "AA444");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");

        // when
        ResultActions resposta = deleteComo("/api/passageiros/" + ana, bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isNoContent());
        assertThat(vinculosDoTransfer(transferId)).isZero();
    }

    @Test
    void deveContinuarExistindoSemPassageiros_quandoPassageiroLigadoFoiExcluido() throws Exception {
        // given
        long ana = criarPassageiro("Ana", "AA444");
        long transferId = criarTransfer(",\"passageiroIds\":[" + ana + "]");
        deleteComo("/api/passageiros/" + ana, bearer(Role.GERENTE)).andExpect(status().isNoContent());

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId, bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiroIds", hasSize(0)));
    }

    // ----- transfer e ordem de servico -----

    @Test
    void deveGravarAFkEConfirmar_quandoTransferEhCriadoDentroDeUmaOs() throws Exception {
        // given
        long osId = criarOs("{\"dataServico\":\"2026-09-25\"}");

        // when
        ResultActions resposta = postJson("/api/transfers", bearer(Role.ATENDENTE), TRANSFER_DIA_25 + ",\"osId\":" + osId + "}");

        // then
        resposta.andExpect(status().isCreated());
        Long fk = jdbcTemplate.queryForObject("SELECT os_id FROM transfers WHERE id = ?", Long.class, idDe(resposta));
        assertThat(fk).isEqualTo(osId);
    }

    @Test
    void deveMostrarAOsEStatusConfirmado_quandoBuscaTransferLigadoAOs() throws Exception {
        // given
        long osId = criarOs("{\"dataServico\":\"2026-09-25\"}");
        long transferId = criarTransfer(",\"osId\":" + osId);

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId, bearer(Role.GERENTE));

        // then
        resposta.andExpect(jsonPath("$.osId").value(osId))
                .andExpect(jsonPath("$.status").value("CONFIRMADO")); // com OS nao fica "aguardando OS"
    }

    @Test
    void deveRetornar400_quandoMudaODiaDeOsQueTemTransfer() throws Exception {
        // given
        long osId = criarOs("{\"dataServico\":\"2026-09-25\"}");
        criarTransfer(",\"osId\":" + osId);

        // when
        // com transfer dentro, a OS nao muda de dia (os dois sairiam de sincronia)
        ResultActions resposta = patchJson("/api/ordens-servico/" + osId, bearer(Role.GERENTE), "{\"dataServico\":\"2026-09-26\"}");

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("Desvincule")));
    }

    @Test
    void deveRetornar400_quandoTransferEntraEmOsDeOutroDia() throws Exception {
        // given
        long osOutroDia = criarOs("{\"dataServico\":\"2026-09-26\"}");
        long transferId = criarTransfer(""); // dia 25

        // when
        ResultActions resposta = patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"osId\":" + osOutroDia + "}");

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("mesmo dia")));
    }

    @Test
    void deveRetornar400_quandoTransferEntraEmOsCancelada() throws Exception {
        // given
        long osCancelada = criarOs("{\"dataServico\":\"2026-09-25\",\"status\":\"CANCELADA\"}");
        long transferId = criarTransfer("");

        // when
        ResultActions resposta = patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"osId\":" + osCancelada + "}");

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("CANCELADA")));
    }

    @Test
    void deveContinuarAguardandoOs_quandoTentativasDeVincularForamRecusadas() throws Exception {
        // given
        long osOutroDia = criarOs("{\"dataServico\":\"2026-09-26\"}");
        long osCancelada = criarOs("{\"dataServico\":\"2026-09-25\",\"status\":\"CANCELADA\"}");
        long transferId = criarTransfer("");
        patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"osId\":" + osOutroDia + "}")
                .andExpect(status().isBadRequest());
        patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"osId\":" + osCancelada + "}")
                .andExpect(status().isBadRequest());

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId, bearer(Role.GERENTE));

        // then
        resposta.andExpect(jsonPath("$.osId").value(nullValue()))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_OS"));
    }

    // ----- auditoria -----

    @Test
    void deveMostrarCriacaoEMudancaDeStatus_quandoGerenteConsultaAuditoriaDeTransfers() throws Exception {
        // given
        long transferId = criarTransfer("");
        patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"status\":\"CONFIRMADO\"}")
                .andExpect(status().isOk());

        // when
        ResultActions resposta = executar(get("/api/auditoria").param("tabela", "transfers").param("size", "100"),
                bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + transferId + ")].mensagem",
                        hasItem(containsString("AGUARDANDO_OS -> CONFIRMADO"))))
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + transferId + ")].mensagem",
                        hasItem(containsString("Transfer criado"))));
    }

    @Test
    void deveRetornar403_quandoAtendenteConsultaAuditoria() throws Exception {
        // when
        ResultActions resposta = getComo("/api/auditoria", bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void deveTrazerSoOHistoricoDoTransfer_quandoFiltraAuditoriaPeloRegistro() throws Exception {
        // given
        long transferId = criarTransfer(",\"valorBase\":150.00");
        long outroId = criarTransfer("");
        patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"valorBase\":180.00}")
                .andExpect(status().isOk());

        // when
        ResultActions resposta = executar(get("/api/auditoria").param("tabela", "transfers")
                .param("registroId", String.valueOf(transferId)), bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].mensagem", hasItem(containsString("valor 150.00 -> 180.00"))))
                .andExpect(jsonPath("$.conteudo[?(@.registroId == " + outroId + ")]").isEmpty());
    }

    @Test
    void naoDeveSobrarLogDeAuditoria_quandoACriacaoFalha() throws Exception {
        // given
        Integer antes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM logs_transfers WHERE tabela_afetada = 'transfers'", Integer.class);

        // when
        // passageiro inexistente: a transacao de criacao aborta, entao nao pode sobrar log
        ResultActions resposta = postJson("/api/transfers", bearer(Role.ATENDENTE),
                "{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"A\","
                        + "\"destino\":\"B\",\"passageiroIds\":[888888]}");

        // then
        resposta.andExpect(status().isNotFound());
        Integer depois = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM logs_transfers WHERE tabela_afetada = 'transfers'", Integer.class);
        assertThat(depois).isEqualTo(antes);
    }

    // ----- pdf, versao e painel -----

    @Test
    void deveGerarPdfComPassageirosSemValorNemDocumento_quandoAtendentePedeOPdfDaOs() throws Exception {
        // given
        long osId = criarOs("{\"dataServico\":\"2026-09-25\"}");
        long ana = criarPassageiro("Ana Relatorio", "PDF111");
        criarTransfer(",\"osId\":" + osId + ",\"valorBase\":321.00,\"passageiroIds\":[" + ana + "]");

        // when
        ResultActions resposta = getComo("/api/ordens-servico/" + osId + "/pdf", bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("ordem-servico-" + osId + ".pdf")));
        PdfReader leitor = new PdfReader(resposta.andReturn().getResponse().getContentAsByteArray());
        String texto = new PdfTextExtractor(leitor).getTextFromPage(1);
        leitor.close();
        assertThat(texto).contains("Ordem de Serviço nº " + osId, "Ana Relatorio", "GRU");
        assertThat(texto).doesNotContain("321", "PDF111"); // nem valor nem documento no papel do motorista
    }

    @Test
    void deveRetornar404_quandoPedePdfDeOsInexistente() throws Exception {
        // when
        ResultActions resposta = getComo("/api/ordens-servico/999999/pdf", bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isNotFound());
    }

    @Test
    void deveSalvarESubirAVersao_quandoEdicaoTrazAVersaoAtual() throws Exception {
        // given
        long transferId = criarTransfer("");
        Long versao = versaoDoTransfer(transferId);

        // when
        // a gerente salva primeiro, em cima da versao que carregou
        ResultActions resposta = patchJson("/api/transfers/" + transferId, bearer(Role.GERENTE),
                "{\"status\":\"CONFIRMADO\",\"versao\":" + versao + "}");

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(versao + 1));
    }

    @Test
    void deveRetornar409_quandoEdicaoTrazVersaoQueOutroUsuarioJaSalvou() throws Exception {
        // given
        long transferId = criarTransfer("");
        Long versao = versaoDoTransfer(transferId);
        patchJson("/api/transfers/" + transferId, bearer(Role.GERENTE), "{\"status\":\"CONFIRMADO\",\"versao\":" + versao + "}")
                .andExpect(status().isOk());

        // when
        // a atendente tinha carregado a mesma versao e tenta salvar depois
        ResultActions resposta = patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE),
                "{\"status\":\"CANCELADO\",\"versao\":" + versao + "}");

        // then
        resposta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de edição"));
    }

    @Test
    void devePreservarAAlteracaoDeQuemSalvouAntes_quandoOutraEdicaoLevou409() throws Exception {
        // given
        long transferId = criarTransfer("");
        Long versao = versaoDoTransfer(transferId);
        patchJson("/api/transfers/" + transferId, bearer(Role.GERENTE), "{\"status\":\"CONFIRMADO\",\"versao\":" + versao + "}")
                .andExpect(status().isOk());
        patchJson("/api/transfers/" + transferId, bearer(Role.ATENDENTE), "{\"status\":\"CANCELADO\",\"versao\":" + versao + "}")
                .andExpect(status().isConflict());

        // when
        ResultActions resposta = getComo("/api/transfers/" + transferId, bearer(Role.GERENTE));

        // then
        resposta.andExpect(jsonPath("$.status").value("CONFIRMADO"));
    }

    @Test
    void deveContarODiaESomarFaturamentoSemCancelados_quandoGerenteAbreOPainel() throws Exception {
        // given
        // data so deste teste, pra nao misturar com os transfers dos outros
        tresTransfersDoPainel("2031-01-15");

        // when
        ResultActions resposta = executar(get("/api/painel/resumo").param("data", "2031-01-15"), bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfers").value(3))
                .andExpect(jsonPath("$.transfersPorStatus.AGUARDANDO_OS").value(2))
                .andExpect(jsonPath("$.transfersPorStatus.CANCELADO").value(1))
                .andExpect(jsonPath("$.faturamentoDia").value(150.50))
                .andExpect(jsonPath("$.faturamentoMes").value(150.50));
    }

    @Test
    void deveEsconderOFaturamento_quandoAtendenteAbreOPainel() throws Exception {
        // given
        // outro dia, so deste teste
        tresTransfersDoPainel("2031-02-15");

        // when
        ResultActions resposta = executar(get("/api/painel/resumo").param("data", "2031-02-15"), bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfers").value(3))
                .andExpect(jsonPath("$.faturamentoDia").value(nullValue()));
    }

    private Long versaoDoTransfer(long transferId) {
        return jdbcTemplate.queryForObject("SELECT versao FROM transfers WHERE id = ?", Long.class, transferId);
    }

    // 100 + 50,50 validos e 999 cancelado, todos no mesmo dia
    private void tresTransfersDoPainel(String data) throws Exception {
        String base = "{\"dataTransfer\":\"" + data + "\",\"horaTransfer\":\"09:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\"";
        for (String extra : List.of(",\"valorBase\":100.00}", ",\"valorBase\":50.50}",
                ",\"valorBase\":999.00,\"status\":\"CANCELADO\"}")) {
            criarPelaApi("/api/transfers", bearer(Role.ATENDENTE), base + extra);
        }
    }
}
