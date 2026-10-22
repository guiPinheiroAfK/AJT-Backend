package com.AJTBackend.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutorizacaoWebTest extends WebTestBase {

    private static final String ORIGEM_DO_FRONT = "http://localhost:4200";

    // ----- sem autenticacao -----

    @Test
    void semTokenRetorna401NoFormatoPadraoSemChamarOService() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers"));

        // Then
        resposta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Falha na autenticação"));
        verifyNoInteractions(transferService);
    }

    @Test
    void loginEhPublico() throws Exception {
        // Given
        String credenciais = "{\"username\":\"admin\",\"senha\":\"admin123\"}";

        // When
        ResultActions resposta = mockMvc.perform(request(HttpMethod.POST, "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciais));

        // Then
        resposta.andExpect(status().isOk());
    }

    @Test
    void meExigeAutenticacao() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/auth/me"));

        // Then
        resposta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(authService);
    }

    // ----- matriz de permissoes -----

    /**
     * Matriz de permissoes (ver javadoc do SecurityConfig): o que cada perfil PODE fazer.
     * Colunas: role, metodo, url, status esperado
     */
    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            // usuarios: so ADMIN
            "ADMIN,     GET,    /api/usuarios,          200",

            // pdf da OS: leitura, qualquer perfil (HU10 e do atendente)
            "ATENDENTE, GET,    /api/ordens-servico/1/pdf, 200",
            "MOTORISTA, GET,    /api/ordens-servico/1/pdf, 200",

            // painel: leitura pra todos (o faturamento e filtrado no service)
            "ATENDENTE, GET,    /api/painel/resumo,     200",
            "MOTORISTA, GET,    /api/painel/resumo,     200",

            // auditoria: so leitura, so ADMIN e GERENTE
            "ADMIN,     GET,    /api/auditoria,         200",
            "GERENTE,   GET,    /api/auditoria,         200",

            // leitura liberada pra qualquer perfil autenticado
            "MOTORISTA, GET,    /api/transfers/1/passageiros, 200",
            "MOTORISTA, GET,    /api/transfers,         200",
            "MOTORISTA, GET,    /api/ordens-servico,    200",
            // "minhas OS do dia" (HU07): so o motorista
            "MOTORISTA, GET,    /api/ordens-servico/minhas, 200",
            "ATENDENTE, GET,    /api/motoristas,        200",

            // DELETE: so ADMIN e GERENTE
            "GERENTE,   DELETE, /api/transfers/1,       204",

            // frota e OS: escrita so ADMIN/GERENTE
            "GERENTE,   POST,   /api/ordens-servico,    201",

            // operacao: ATENDENTE pode escrever
            "ATENDENTE, POST,   /api/transfers,         201",
            "ATENDENTE, GET,    /api/passageiros,       200",

            // paradas: MOTORISTA so PATCH (campos restritos no service)
            "MOTORISTA, PATCH,  /api/paradas-os/1,      200",
    })
    void perfilComPermissaoRecebeStatusDeSucesso(String role, String metodo, String url, int esperado) throws Exception {
        // When
        ResultActions resposta = chamarComoPerfil(role, metodo, url);

        // Then
        resposta.andExpect(status().is(esperado));
    }

    /**
     * Matriz de permissoes: o que cada perfil NAO pode fazer.
     * Colunas: role, metodo, url (sempre 403)
     */
    @ParameterizedTest(name = "{0} {1} {2} -> 403")
    @CsvSource({
            // usuarios: so ADMIN
            "GERENTE,   GET,    /api/usuarios",
            "ATENDENTE, GET,    /api/usuarios/1",

            // auditoria: so ADMIN e GERENTE
            "ATENDENTE, GET,    /api/auditoria",
            "MOTORISTA, GET,    /api/auditoria",

            // "minhas OS do dia" (HU07): so o motorista
            "GERENTE,   GET,    /api/ordens-servico/minhas",
            "ATENDENTE, GET,    /api/ordens-servico/minhas",

            // DELETE: so ADMIN e GERENTE
            "ATENDENTE, DELETE, /api/transfers/1",
            "MOTORISTA, DELETE, /api/paradas-os/1",

            // frota e OS: escrita so ADMIN/GERENTE
            "ATENDENTE, POST,   /api/ordens-servico",
            "ATENDENTE, PUT,    /api/motoristas/1",
            "MOTORISTA, PATCH,  /api/veiculos/1",

            // operacao: MOTORISTA nao escreve em transfers nem enxerga passageiros
            "MOTORISTA, POST,   /api/transfers",
            "MOTORISTA, PATCH,  /api/passageiros/1",
            "MOTORISTA, GET,    /api/passageiros",
            "MOTORISTA, GET,    /api/passageiros/1",

            // paradas: MOTORISTA so PATCH, ATENDENTE nem isso
            "MOTORISTA, POST,   /api/paradas-os",
            "ATENDENTE, PATCH,  /api/paradas-os/1",
    })
    void perfilSemPermissaoRecebe403SemChegarNoService(String role, String metodo, String url) throws Exception {
        // When
        ResultActions resposta = chamarComoPerfil(role, metodo, url);

        // Then
        resposta.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNenhumServiceFoiChamado();
    }

    @Test
    void auditoriaNaoAceitaEscritaNemParaAdmin() throws Exception {
        // When
        ResultActions resposta = chamarComoPerfil("ADMIN", "POST", "/api/auditoria");

        // Then
        resposta.andExpect(status().isMethodNotAllowed());
        verify405SemService();
    }

    // ----- cors -----

    @Test
    void preflightCorsDoFrontEhAceito() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(options("/api/transfers")
                .header(HttpHeaders.ORIGIN, ORIGEM_DO_FRONT)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_DO_FRONT));
    }

    @Test
    void preflightCorsDeOrigemDesconhecidaEhRecusado() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(options("/api/transfers")
                .header(HttpHeaders.ORIGIN, "https://site-malicioso.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"));

        // Then
        resposta.andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // ----- headers de seguranca -----

    @Test
    void respostasTemOHeaderContraSniffingDeTipo() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers").with(user("teste").roles("ADMIN")));

        // Then
        resposta.andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void respostasTemOHeaderContraClickjacking() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers").with(user("teste").roles("ADMIN")));

        // Then
        resposta.andExpect(header().string("X-Frame-Options", "DENY"));
    }

    // ----- apoio -----

    private ResultActions chamarComoPerfil(String role, String metodo, String url) throws Exception {
        return mockMvc.perform(request(HttpMethod.valueOf(metodo), url)
                .with(user("teste").roles(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoValido(url)));
    }

    private void verifyNenhumServiceFoiChamado() {
        verifyNoInteractions(authService, usuarioService, motoristaService, veiculoService, passageiroService,
                transferService, pontoColetaService, ordemServicoService, paradaOsService, cotacaoService,
                auditoriaService, painelService, relatorioOrdemServicoService);
    }

    private void verify405SemService() {
        verifyNoInteractions(auditoriaService);
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