package com.AJTBackend.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: a matriz de permissoes inteira do SecurityConfig — 401 sem
 * token, login e "auth/me" publicos/protegidos certo, a matriz de perfil x
 * metodo x rota (via @CsvSource, um caso por linha) e o cors (preflight
 * aceito pra origem do front, recusado pra origem desconhecida).
 *
 * como rodar: @WebMvcTest com o usuario simulado por with(user(...).roles(...))
 * (sem login de verdade). sem banco, sem docker. roda com "mvn test".
 *
 * por que existe: e o unico lugar que testa TODA a tabela de perfis de uma
 * vez (a mesma tabela documentada no javadoc do SecurityConfig) — se
 * alguem mudar uma regra de acesso sem atualizar aqui, o teste quebra e
 * avisa antes de virar um bug de seguranca em producao.
 */
class AutorizacaoWebTest extends WebTestBase {

    @Test
    void semTokenRetorna401NoFormatoPadrao() throws Exception {
        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Falha na autenticação"));
    }

    @Test
    void loginEhPublico() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"senha\":\"admin123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void meExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    /**
     * Matriz de permissoes (ver javadoc do SecurityConfig).
     * Colunas: role, metodo, url, status esperado
     */
    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            // usuarios: so ADMIN
            "ADMIN,     GET,    /api/usuarios,          200",
            "GERENTE,   GET,    /api/usuarios,          403",
            "ATENDENTE, GET,    /api/usuarios/1,        403",

            // auditoria: so leitura, so ADMIN e GERENTE
            "ADMIN,     GET,    /api/auditoria,         200",
            "GERENTE,   GET,    /api/auditoria,         200",
            "ATENDENTE, GET,    /api/auditoria,         403",
            "MOTORISTA, GET,    /api/auditoria,         403",
            "ADMIN,     POST,   /api/auditoria,         405",

            // leitura liberada pra qualquer perfil autenticado
            "MOTORISTA, GET,    /api/transfers/1/passageiros, 200",
            "MOTORISTA, GET,    /api/transfers,         200",
            "MOTORISTA, GET,    /api/ordens-servico,    200",
            "ATENDENTE, GET,    /api/motoristas,        200",

            // DELETE: so ADMIN e GERENTE
            "GERENTE,   DELETE, /api/transfers/1,       204",
            "ATENDENTE, DELETE, /api/transfers/1,       403",
            "MOTORISTA, DELETE, /api/paradas-os/1,      403",

            // frota e OS: escrita so ADMIN/GERENTE
            "GERENTE,   POST,   /api/ordens-servico,    201",
            "ATENDENTE, POST,   /api/ordens-servico,    403",
            "ATENDENTE, PUT,    /api/motoristas/1,      403",
            "MOTORISTA, PATCH,  /api/veiculos/1,        403",

            // operacao: ATENDENTE pode escrever
            "ATENDENTE, POST,   /api/transfers,         201",
            "MOTORISTA, POST,   /api/transfers,         403",
            "MOTORISTA, PATCH,  /api/passageiros/1,     403",

            // paradas: MOTORISTA so PATCH (campos restritos no service)
            "MOTORISTA, PATCH,  /api/paradas-os/1,      200",
            "MOTORISTA, POST,   /api/paradas-os,        403",
            "ATENDENTE, PATCH,  /api/paradas-os/1,      403",
    })
    void matrizDePermissoes(String role, String metodo, String url, int esperado) throws Exception {
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(metodo), url)
                .with(user("teste").roles(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoValido(url));

        mockMvc.perform(req).andExpect(status().is(esperado));
    }

    @Test
    void preflightCorsDoFrontEhAceito() throws Exception {
        mockMvc.perform(options("/api/transfers")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void preflightCorsDeOrigemDesconhecidaEhRecusado() throws Exception {
        mockMvc.perform(options("/api/transfers")
                        .header(HttpHeaders.ORIGIN, "https://site-malicioso.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void respostasTemHeadersDeSeguranca() throws Exception {
        mockMvc.perform(get("/api/transfers").with(user("teste").roles("ADMIN")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    private static String corpoValido(String url) {
        if (url.startsWith("/api/transfers")) {
            return TRANSFER_VALIDO;
        }
        if (url.startsWith("/api/ordens-servico")) {
            return "{\"dataServico\":\"2026-09-20\"}";
        }
        if (url.startsWith("/api/paradas-os")) {
            return "{\"statusParada\":\"CONCLUIDA\"}";
        }
        return "{}";
    }
}
