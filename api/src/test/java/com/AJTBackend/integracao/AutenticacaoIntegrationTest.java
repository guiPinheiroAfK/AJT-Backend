package com.AJTBackend.integracao;

import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.awaitility.Awaitility.await;
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
        // When
        ResultActions resposta = tentarLogin("admin", "admin123");

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.trocarSenha").value(true));
    }

    @Test
    void meDevolveOUsuarioDoTokenComOUltimoLogin() throws Exception {
        // Given
        Usuario usuario = criarUsuario(Role.ATENDENTE, "senha-inicial-1");
        String token = login(usuario.getUsername(), "senha-inicial-1");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(usuario.getUsername()))
                .andExpect(jsonPath("$.ultimoLogin").isNotEmpty());
    }

    @Test
    void trocaDeSenhaInvalidaOTokenAntigo() throws Exception {
        // Given
        Usuario usuario = criarUsuario(Role.ATENDENTE, "senha-inicial-1");
        String tokenAntigo = login(usuario.getUsername(), "senha-inicial-1");
        trocarSenha(tokenAntigo, "senha-inicial-1", "senha-nova-123");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenAntigo));

        // Then
        resposta.andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDevolvidoNaTrocaDeSenhaJaValeNaHora() throws Exception {
        // Given
        Usuario usuario = criarUsuario(Role.ATENDENTE, "senha-inicial-1");
        String tokenAntigo = login(usuario.getUsername(), "senha-inicial-1");
        String tokenNovo = trocarSenha(tokenAntigo, "senha-inicial-1", "senha-nova-123");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenNovo));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(usuario.getUsername()));
    }

    @Test
    void usuarioDesativadoPerdeAcessoNaHora() throws Exception {
        // Given: o gerente logado e depois desativado pelo admin, com o token ainda dentro da validade
        Usuario usuario = criarUsuario(Role.GERENTE, "senha-inicial-1");
        String token = login(usuario.getUsername(), "senha-inicial-1");
        alterarComoAdmin(usuario, "{\"ativo\":false}");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers").header("Authorization", "Bearer " + token));

        // Then
        resposta.andExpect(status().isUnauthorized());
    }

    @Test
    void perfilAlteradoValeSemPrecisarDeNovoLogin() throws Exception {
        // Given: o token ainda diz GERENTE, mas o banco agora diz MOTORISTA
        Usuario usuario = criarUsuario(Role.GERENTE, "senha-inicial-1");
        String token = login(usuario.getUsername(), "senha-inicial-1");
        alterarComoAdmin(usuario, "{\"role\":\"MOTORISTA\"}");

        // When
        ResultActions resposta = mockMvc.perform(post("/api/ordens-servico").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataServico\":\"2026-09-20\"}"));

        // Then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void bloqueiaOLoginAposTresSenhasErradasMesmoComASenhaCerta() throws Exception {
        // Given: ajt.login.max-tentativas=3 no profile test
        Usuario usuario = criarUsuario(Role.ATENDENTE, "senha-certa-123");
        for (int i = 0; i < 3; i++) {
            tentarLogin(usuario.getUsername(), "errada");
        }

        // When
        ResultActions resposta = tentarLogin(usuario.getUsername(), "senha-certa-123");

        // Then
        resposta.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("Muitas tentativas"));
    }

    private ResultActions tentarLogin(String username, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"senha\":\"" + senha + "\"}"));
    }

    private String login(String username, String senha) throws Exception {
        String json = tentarLogin(username, senha)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("token").asText();
    }

    private String trocarSenha(String token, String senhaAtual, String novaSenha) throws Exception {
        // garante segundo diferente entre a emissao do token e a troca (o iat do JWT tem precisao de segundos)
        long emitidoEm = jwtService.validarToken(token).getIssuedAt().toInstant().getEpochSecond();
        await().atMost(Duration.ofSeconds(3)).until(() -> Instant.now().getEpochSecond() > emitidoEm);
        String json = mockMvc.perform(put("/api/auth/senha").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + senhaAtual + "\",\"novaSenha\":\"" + novaSenha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    private void alterarComoAdmin(Usuario usuario, String json) throws Exception {
        mockMvc.perform(patch("/api/usuarios/" + usuario.getId()).header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }
}
