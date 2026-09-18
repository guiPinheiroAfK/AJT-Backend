package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
