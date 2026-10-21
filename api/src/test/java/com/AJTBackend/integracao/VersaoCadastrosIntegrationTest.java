package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

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

    private JsonNode json(String corpo) throws Exception {
        return objectMapper.readTree(corpo);
    }

    @Test
    void passageiroComVersaoVelhaRetorna409EPreservaQuemSalvouAntes() throws Exception {
        String criado = mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"V1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versao").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = json(criado).get("id").asLong();

        mockMvc.perform(patch("/api/passageiros/" + id).header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Ana Maria\",\"versao\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(1));

        mockMvc.perform(patch("/api/passageiros/" + id).header("Authorization", bearer(Role.GERENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Ana Paula\",\"versao\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de edição"));

        mockMvc.perform(get("/api/passageiros/" + id).header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(jsonPath("$.nome").value("Ana Maria"));
    }

    @Test
    void veiculoSemVersaoContinuaFuncionandoParaClienteAntigo() throws Exception {
        String placa = ("V" + UUID.randomUUID().toString().replace("-", "")).substring(0, 7).toUpperCase();
        String criado = mockMvc.perform(post("/api/veiculos").header("Authorization", bearer(Role.GERENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Van\",\"placa\":\"" + placa + "\",\"capacidade\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json(criado).get("id").asLong();

        // sem "versao" no corpo: nao confere (cliente antigo), so grava e devolve a versao nova
        mockMvc.perform(patch("/api/veiculos/" + id).header("Authorization", bearer(Role.GERENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacidade\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void loginNaoContaComoEdicaoDoUsuario() throws Exception {
        String username = "ver-" + UUID.randomUUID().toString().substring(0, 8);
        String criado = mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"username\":\"" + username + "\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json(criado).get("id").asLong();
        long versao = json(criado).get("versao").asLong();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isOk());

        // o admin que abriu o cadastro antes do login ainda consegue salvar
        mockMvc.perform(patch("/api/usuarios/" + id).header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste 2\",\"versao\":" + versao + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(versao + 1));
    }
}
