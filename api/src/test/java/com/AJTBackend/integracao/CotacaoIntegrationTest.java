package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: a integracao real com a api de cambio (simulada com
 * wiremock) — cache funcionando (so 1 chamada http pra 3 consultas iguais),
 * erro 500 da api virando 502 pro cliente, timeout respeitado (502 tambem)
 * e o transfer em moeda estrangeira sendo criado mesmo com a api fora do
 * ar, ou convertido certo quando ela responde.
 *
 * como rodar: precisa de docker (herda de IntegracaoTestBase). a url da
 * api de cambio (ajt.cotacao.url) e trocada pela do wiremock via
 * @DynamicPropertySource, entao nenhum teste sai pra internet de verdade.
 * roda com "mvn test"; sem docker, a classe inteira e pulada.
 *
 * por que existe: o CotacaoServiceTest ja cobre a logica com mock, mas so
 * subindo o contexto real do spring dá pra testar o @Cacheable (proxy do
 * spring) e o timeout de verdade configurado no feign — nenhum dos dois
 * aparece testando so a classe isolada.
 */
class CotacaoIntegrationTest extends IntegracaoTestBase {

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void limpar() {
        cacheManager.getCache("cotacoes").clear();
        COTACAO_API.resetAll();
    }

    @Test
    void consultaApiExternaUmaVezEUsaCacheNasSeguintes() throws Exception {
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest"))
                .withQueryParam("base", equalTo("USD"))
                .withQueryParam("symbols", equalTo("BRL"))
                .willReturn(okJson("{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-16\",\"rates\":{\"BRL\":5.4321}}")));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/cotacao")
                            .param("de", "usd").param("para", "BRL")
                            .header("Authorization", bearer(Role.ATENDENTE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taxa").value(5.4321))
                    .andExpect(jsonPath("$.data").value("2026-09-16"));
        }

        COTACAO_API.verify(1, getRequestedFor(urlPathEqualTo("/latest")));
    }

    @Test
    void erroNaApiExternaRetorna502() throws Exception {
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest")).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/cotacao")
                        .param("de", "EUR").param("para", "BRL")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void apiLentaRespeitaTimeoutERetorna502() throws Exception {
        // read-timeout no profile test = 1s
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(okJson("{\"rates\":{\"BRL\":7.0}}").withFixedDelay(3000)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/cotacao")
                        .param("de", "GBP").param("para", "BRL")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isBadGateway());
    }

    @Test
    void transferEmMoedaEstrangeiraEhCriadoMesmoComApiForaDoAr() throws Exception {
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest")).willReturn(aResponse().withStatus(503)));

        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"valorOriginal\":100.00,\"moedaOrigem\":\"CHF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorOriginal").value(100.00))
                .andExpect(jsonPath("$.valorBase").doesNotExist());
    }

    @Test
    void transferEmMoedaEstrangeiraConverteParaReais() throws Exception {
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest"))
                .withQueryParam("base", equalTo("ARS"))
                .willReturn(okJson("{\"amount\":1.0,\"base\":\"ARS\",\"date\":\"2026-09-16\",\"rates\":{\"BRL\":0.0055}}")));

        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"valorOriginal\":10000.00,\"moedaOrigem\":\"ars\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moedaOrigem").value("ARS"))
                .andExpect(jsonPath("$.valorBase").value(55.00));
    }
}
