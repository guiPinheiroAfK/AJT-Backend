package com.AJTBackend.integracao;

import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: o fluxo de autenticacao ponta a ponta contra um banco real —
 * admin do seed precisando trocar senha, login -> /auth/me -> troca de
 * senha invalidando o token antigo, usuario desativado perdendo acesso na
 * hora, perfil alterado valendo sem precisar logar de novo, e o bloqueio
 * apos tentativas erradas de login.
 *
 * como rodar: precisa de docker (herda de IntegracaoTestBase). roda com
 * "mvn test"; sem docker, a classe inteira e pulada.
 *
 * por que existe: o AuthServiceTest e o JwtAuthenticationFilterTest ja
 * cobrem essa logica com mock — este teste prova que as pecas realmente se
 * encaixam quando passam pelo http, pelo spring security e por um banco de
 * verdade, sem nenhum atalho de teste no meio.
 */
class AutenticacaoIntegrationTest extends IntegracaoTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminDoSeedPrecisaTrocarSenhaNoPrimeiroAcesso() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"senha\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.trocarSenha").value(true));
    }

    @Test
    void loginMeETrocaDeSenhaInvalidandoTokenAntigo() throws Exception {
        Usuario usuario = criarUsuario(Role.ATENDENTE, "senha-inicial-1");

        String tokenAntigo = login(usuario.getUsername(), "senha-inicial-1");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenAntigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(usuario.getUsername()))
                .andExpect(jsonPath("$.ultimoLogin").isNotEmpty());

        // garante segundo diferente entre emissao do token antigo e a troca (iat tem precisao de segundos)
        Thread.sleep(1100);

        String resposta = mockMvc.perform(put("/api/auth/senha").header("Authorization", "Bearer " + tokenAntigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senha-inicial-1\",\"novaSenha\":\"senha-nova-123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenNovo = objectMapper.readTree(resposta).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenAntigo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenNovo))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioDesativadoPerdeAcessoNaHora() throws Exception {
        Usuario usuario = criarUsuario(Role.GERENTE, "senha-inicial-1");
        String token = login(usuario.getUsername(), "senha-inicial-1");

        mockMvc.perform(get("/api/transfers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/usuarios/" + usuario.getId()).header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/transfers").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilAlteradoValeSemPrecisarDeNovoLogin() throws Exception {
        Usuario usuario = criarUsuario(Role.GERENTE, "senha-inicial-1");
        String token = login(usuario.getUsername(), "senha-inicial-1");

        mockMvc.perform(patch("/api/usuarios/" + usuario.getId()).header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MOTORISTA\"}"))
                .andExpect(status().isOk());

        // token ainda diz GERENTE, mas o banco diz MOTORISTA
        mockMvc.perform(post("/api/ordens-servico").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataServico\":\"2026-09-20\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bloqueiaLoginAposTentativasErradas() throws Exception {
        Usuario usuario = criarUsuario(Role.ATENDENTE, "senha-certa-123");
        String errado = "{\"username\":\"" + usuario.getUsername() + "\",\"senha\":\"errada\"}";

        for (int i = 0; i < 3; i++) { // ajt.login.max-tentativas=3 no profile test
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(errado))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario.getUsername() + "\",\"senha\":\"senha-certa-123\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private String login(String username, String senha) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("token").asText();
    }
}
