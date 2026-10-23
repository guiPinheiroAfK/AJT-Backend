package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: o RNF008 nos cadastros (passageiro, veiculo, usuario...): a resposta
 * traz a versao, a edicao com a versao certa passa e sobe a versao, e a edicao com
 * uma versao velha (outra pessoa salvou antes) volta 409 sem sobrescrever. tambem
 * confere que o login nao conta como edicao do usuario (ultimoLogin fora do lock).
 *
 * como rodar: herda IntegracaoTestBase (testcontainers); precisa do docker.
 *
 * por que existe: transfer e OS ja tinham esse teste; os cadastros ganharam a
 * mesma regra depois, e o jeito de quebrar ela sem perceber e esquecer o flush
 * (a resposta voltaria com a versao antiga e a proxima edicao daria 409 a toa).
 */
class VersaoCadastrosIntegrationTest extends IntegracaoTestBase {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private JsonNode json(String corpo) throws Exception {
        return objectMapper.readTree(corpo);
    }

    private JsonNode criar(String url, Role perfil, String corpo) throws Exception {
        return json(mockMvc.perform(post(url).header("Authorization", bearer(perfil))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions editar(String url, Role perfil, String corpo) throws Exception {
        return mockMvc.perform(patch(url).header("Authorization", bearer(perfil))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    void passageiroComVersaoVelhaRetorna409EPreservaQuemSalvouAntes() throws Exception {
        // Given: a atendente e a gerente abrem o passageiro na versao 0, e a atendente salva primeiro
        long id = criar("/api/passageiros", Role.ATENDENTE,
                "{\"nome\":\"Ana\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"V1\"}").get("id").asLong();
        editar("/api/passageiros/" + id, Role.ATENDENTE, "{\"nome\":\"Ana Maria\",\"versao\":0}")
                .andExpect(jsonPath("$.versao").value(1));

        // When: a gerente salva em cima da versao 0, que ja ficou velha
        ResultActions resposta = editar("/api/passageiros/" + id, Role.GERENTE, "{\"nome\":\"Ana Paula\",\"versao\":0}");

        // Then
        resposta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de edição"));
        assertThat(jdbcTemplate.queryForObject("SELECT nome FROM passageiros WHERE id = ?", String.class, id))
                .isEqualTo("Ana Maria");
    }

    @Test
    void veiculoSemVersaoContinuaFuncionandoParaClienteAntigo() throws Exception {
        // Given
        String placa = ("V" + UUID.randomUUID().toString().replace("-", "")).substring(0, 7).toUpperCase();
        long id = criar("/api/veiculos", Role.GERENTE,
                "{\"label\":\"Van\",\"placa\":\"" + placa + "\",\"capacidade\":10}").get("id").asLong();

        // When: sem "versao" no corpo nao confere (cliente antigo), so grava
        ResultActions resposta = editar("/api/veiculos/" + id, Role.GERENTE, "{\"capacidade\":12}");

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidade").value(12))
                .andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void loginNaoContaComoEdicaoDoUsuario() throws Exception {
        // Given: o admin abre o cadastro e, antes de salvar, o proprio usuario faz login
        String username = "ver-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode criado = criar("/api/usuarios", Role.ADMIN,
                "{\"nome\":\"Teste\",\"username\":\"" + username + "\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\"}");
        long id = criado.get("id").asLong();
        long versao = criado.get("versao").asLong();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isOk());

        // When
        ResultActions resposta = editar("/api/usuarios/" + id, Role.ADMIN, "{\"nome\":\"Teste 2\",\"versao\":" + versao + "}");

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(versao + 1));
    }
}
