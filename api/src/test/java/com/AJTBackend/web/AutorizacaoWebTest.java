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

/*
 * o que testa: a matriz de permissoes do SecurityConfig rota a rota (o que cada
 * perfil pode e nao pode), o 401 sem token no formato padrao, login publico, CORS
 * do front e os headers de seguranca (nosniff, DENY).
 *
 * como rodar: @WebMvcTest (WebTestBase), services mockados. sem banco. "mvn test".
 *
 * por que existe: uma linha errada no SecurityConfig libera DELETE pro atendente ou
 * esconde a OS do motorista. o 403 tem que sair antes de chegar no service.
 */
class AutorizacaoWebTest extends WebTestBase {

    private static final String ORIGEM_DO_FRONT = "http://localhost:4200";

    // ----- sem autenticacao -----

    @Test
    void deveRetornar401NoFormatoPadraoSemChamarOService_quandoNaoHaToken() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/transfers"));

        // then
        resposta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Falha na autenticação"));
        verifyNoInteractions(transferService);
    }

    @Test
    void deveDeixarPassar_quandoLoginSemToken() throws Exception {
        // given
        String credenciais = "{\"username\":\"admin\",\"senha\":\"admin123\"}";

        // when
        ResultActions resposta = mockMvc.perform(request(HttpMethod.POST, "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciais));

        // then
        resposta.andExpect(status().isOk());
    }

    @Test
    void deveRetornar401_quandoMeSemToken() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/auth/me"));

        // then
        resposta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(authService);
    }

    // ----- matriz de permissoes -----

    // matriz de permissoes (ver javadoc do SecurityConfig): o que cada perfil PODE fazer
    // colunas: role, metodo, url, status esperado
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
    void deveRetornarSucesso_quandoPerfilTemPermissaoNaRota(String role, String metodo, String url, int esperado) throws Exception {
        // when
        ResultActions resposta = chamarComoPerfil(role, metodo, url);

        // then
        resposta.andExpect(status().is(esperado));
    }

    // matriz de permissoes: o que cada perfil NAO pode fazer
    // colunas: role, metodo, url (sempre 403)
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
    void deveRetornar403SemChegarNoService_quandoPerfilNaoTemPermissaoNaRota(String role, String metodo, String url) throws Exception {
        // when
        ResultActions resposta = chamarComoPerfil(role, metodo, url);

        // then
        resposta.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNenhumServiceFoiChamado();
    }

    @Test
    void deveRetornar405SemChamarOService_quandoAdminTentaEscreverNaAuditoria() throws Exception {
        // when
        ResultActions resposta = chamarComoPerfil("ADMIN", "POST", "/api/auditoria");

        // then
        resposta.andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(auditoriaService);
    }

    // ----- cors -----

    @Test
    void deveAceitarOPreflight_quandoOrigemEhOFront() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(options("/api/transfers")
                .header(HttpHeaders.ORIGIN, ORIGEM_DO_FRONT)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_DO_FRONT));
    }

    @Test
    void deveRecusarOPreflight_quandoOrigemEhDesconhecida() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(options("/api/transfers")
                .header(HttpHeaders.ORIGIN, "https://site-malicioso.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"));

        // then
        resposta.andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // ----- headers de seguranca -----

    @Test
    void deveMandarNosniff_quandoResponderQualquerRota() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/transfers").with(user("teste").roles("ADMIN")));

        // then
        resposta.andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void deveMandarFrameOptionsDeny_quandoResponderQualquerRota() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/transfers").with(user("teste").roles("ADMIN")));

        // then
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
