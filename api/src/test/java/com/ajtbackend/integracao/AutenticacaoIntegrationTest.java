package com.ajtbackend.integracao;

import com.ajtbackend.model.Usuario;
import com.ajtbackend.model.enums.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: o fluxo de autenticacao ponta a ponta contra um banco real —
 * admin do seed precisando trocar senha, login -> /auth/me -> troca de
 * senha invalidando o token antigo, usuario desativado perdendo acesso na
 * hora, perfil alterado valendo sem precisar logar de novo, o bloqueio
 * apos tentativas erradas de login, e as premissas do CSRF desligado
 * (ADR-0015): o login nao abre sessao nem devolve cookie, e cookie sem o
 * token nao autoriza escrita.
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

    private static final String SENHA_INICIAL = "senha-inicial-1";

    @Test
    void deveExigirTrocaDeSenha_quandoAdminDoSeedFazOPrimeiroLogin() throws Exception {
        // when
        ResultActions resposta = tentarLogin("admin", "admin123");

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.trocarSenha").value(true));
    }

    @Test
    void deveDevolverOUsuarioComUltimoLogin_quandoMeRecebeTokenDoLogin() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.ATENDENTE, SENHA_INICIAL);
        String token = login(usuario.getUsername(), SENHA_INICIAL);

        // when
        ResultActions resposta = getComo("/api/auth/me", "Bearer " + token);

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(usuario.getUsername()))
                .andExpect(jsonPath("$.ultimoLogin").isNotEmpty());
    }

    @Test
    void deveDevolverTokenNovo_quandoTrocaASenhaComASenhaAtualCerta() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.ATENDENTE, SENHA_INICIAL);
        String token = login(usuario.getUsername(), SENHA_INICIAL);

        // when
        ResultActions resposta = trocarSenha(token);

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void deveRecusarOTokenAntigo_quandoSenhaFoiTrocada() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.ATENDENTE, SENHA_INICIAL);
        String tokenAntigo = login(usuario.getUsername(), SENHA_INICIAL);
        trocarSenha(tokenAntigo).andExpect(status().isOk());

        // when
        ResultActions resposta = getComo("/api/auth/me", "Bearer " + tokenAntigo);

        // then
        resposta.andExpect(status().isUnauthorized());
    }

    @Test
    void deveAceitarOTokenNovo_quandoSenhaFoiTrocada() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.ATENDENTE, SENHA_INICIAL);
        String tokenAntigo = login(usuario.getUsername(), SENHA_INICIAL);
        String tokenNovo = corpoDe(trocarSenha(tokenAntigo)).get("token").asText();

        // when
        ResultActions resposta = getComo("/api/auth/me", "Bearer " + tokenNovo);

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(usuario.getUsername()));
    }

    @Test
    void deveAceitarOToken_quandoUsuarioEstaAtivo() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.GERENTE, SENHA_INICIAL);
        String token = login(usuario.getUsername(), SENHA_INICIAL);

        // when
        ResultActions resposta = getComo("/api/transfers", "Bearer " + token);

        // then
        resposta.andExpect(status().isOk());
    }

    @Test
    void deveRecusarOTokenNaHora_quandoUsuarioFoiDesativado() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.GERENTE, SENHA_INICIAL);
        String token = login(usuario.getUsername(), SENHA_INICIAL);
        patchJson("/api/usuarios/" + usuario.getId(), bearer(Role.ADMIN), "{\"ativo\":false}")
                .andExpect(status().isOk());

        // when
        ResultActions resposta = getComo("/api/transfers", "Bearer " + token);

        // then
        resposta.andExpect(status().isUnauthorized());
    }

    @Test
    void deveValerONovoPerfilSemNovoLogin_quandoPerfilFoiAlterado() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.GERENTE, SENHA_INICIAL);
        String token = login(usuario.getUsername(), SENHA_INICIAL);
        patchJson("/api/usuarios/" + usuario.getId(), bearer(Role.ADMIN), "{\"role\":\"MOTORISTA\"}")
                .andExpect(status().isOk());

        // when
        // token ainda diz GERENTE, mas o banco diz MOTORISTA
        ResultActions resposta = postJson("/api/ordens-servico", "Bearer " + token, "{\"dataServico\":\"2026-09-20\"}");

        // then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void deveBloquearMesmoComSenhaCerta_quandoErrouASenhaVariasVezes() throws Exception {
        // given
        Usuario usuario = criarUsuario(Role.ATENDENTE, "senha-certa-123");
        List<Integer> statusDasErradas = new ArrayList<>();
        for (int i = 0; i < 3; i++) { // ajt.login.max-tentativas=3 no profile test
            statusDasErradas.add(tentarLogin(usuario.getUsername(), "errada").andReturn().getResponse().getStatus());
        }

        // when
        ResultActions resposta = tentarLogin(usuario.getUsername(), "senha-certa-123");

        // then
        assertThat(statusDasErradas).containsOnly(401);
        resposta.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("Muitas tentativas"));
    }

    @Test
    void naoDeveAbrirSessaoNemDevolverCookie_quandoLoginDaCerto() throws Exception {
        // given
        // premissa do CSRF desligado (ADR-0015): nao existe credencial que o navegador mande sozinho
        Usuario usuario = criarUsuario(Role.ATENDENTE, SENHA_INICIAL);

        // when
        ResultActions resposta = tentarLogin(usuario.getUsername(), SENHA_INICIAL);

        // then
        resposta.andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(resposta.andReturn().getRequest().getSession(false)).isNull();
    }

    @Test
    void deveRetornar401_quandoEscritaChegaSoComCookieSemToken() throws Exception {
        // given
        // o que um site de terceiros consegue forjar: o navegador anexa os cookies, mas nao o header Authorization
        Cookie cookieDoNavegador = new Cookie("JSESSIONID", "sessao-qualquer");

        // when
        ResultActions resposta = mockMvc.perform(post("/api/transfers").cookie(cookieDoNavegador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataTransfer\":\"2026-09-25\",\"horaTransfer\":\"14:30:00\",\"origem\":\"A\",\"destino\":\"B\"}"));

        // then
        resposta.andExpect(status().isUnauthorized());
    }

    private ResultActions tentarLogin(String username, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"senha\":\"" + senha + "\"}"));
    }

    // preparo: loga e devolve o token (confere o 200 pra o teste falhar no lugar certo)
    private String login(String username, String senha) throws Exception {
        ResultActions resposta = tentarLogin(username, senha);
        resposta.andExpect(status().isOk());
        return corpoDe(resposta).get("token").asText();
    }

    private ResultActions trocarSenha(String token) throws Exception {
        // garante segundo diferente entre a emissao do token e a troca (o iat do JWT tem precisao de segundos)
        long emitidoEm = jwtService.validarToken(token).getIssuedAt().toInstant().getEpochSecond();
        await().atMost(Duration.ofSeconds(3)).until(() -> Instant.now().getEpochSecond() > emitidoEm);
        return putJson("/api/auth/senha", "Bearer " + token,
                "{\"senhaAtual\":\"" + SENHA_INICIAL + "\",\"novaSenha\":\"senha-nova-123\"}");
    }
}
