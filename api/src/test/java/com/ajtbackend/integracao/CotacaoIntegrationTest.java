package com.ajtbackend.integracao;

import com.ajtbackend.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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

    private ResultActions cotacao(String de, String para) throws Exception {
        return getComo("/api/cotacao?de=" + de + "&para=" + para, bearer(Role.ATENDENTE));
    }

    private static String transferEm(String moeda, String valorOriginal) {
        return "{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\","
                + "\"valorOriginal\":" + valorOriginal + ",\"moedaOrigem\":\"" + moeda + "\"}";
    }

    @Test
    void deveChamarAApiUmaVezSo_quandoAMesmaCotacaoEhPedidaTresVezes() throws Exception {
        // given
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest"))
                .withQueryParam("base", equalTo("USD"))
                .withQueryParam("symbols", equalTo("BRL"))
                .willReturn(okJson("{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-16\",\"rates\":{\"BRL\":5.4321}}")));

        // when
        List<ResultActions> respostas = List.of(cotacao("usd", "BRL"), cotacao("usd", "BRL"), cotacao("usd", "BRL"));

        // then
        for (ResultActions resposta : respostas) {
            resposta.andExpect(status().isOk())
                    .andExpect(jsonPath("$.taxa").value(5.4321))
                    .andExpect(jsonPath("$.data").value("2026-09-16"));
        }
        COTACAO_API.verify(1, getRequestedFor(urlPathEqualTo("/latest")));
    }

    @Test
    void deveRetornar502_quandoApiExternaDevolve500() throws Exception {
        // given
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest")).willReturn(aResponse().withStatus(500)));

        // when
        ResultActions resposta = cotacao("EUR", "BRL");

        // then
        resposta.andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void deveRetornar502PeloTimeout_quandoApiExternaEstaLenta() throws Exception {
        // given
        // read-timeout no profile test = 1s
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(okJson("{\"rates\":{\"BRL\":7.0}}").withFixedDelay(3000)));

        // when
        ResultActions resposta = cotacao("GBP", "BRL");

        // then
        resposta.andExpect(status().isBadGateway());
    }

    @Test
    void deveCriarSemValorBase_quandoApiDeCambioEstaForaDoAr() throws Exception {
        // given
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest")).willReturn(aResponse().withStatus(503)));

        // when
        ResultActions resposta = postJson("/api/transfers", bearer(Role.ATENDENTE), transferEm("CHF", "100.00"));

        // then
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorOriginal").value(100.00))
                .andExpect(jsonPath("$.valorBase").doesNotExist());
    }

    @Test
    void deveConverterParaReais_quandoApiDeCambioResponde() throws Exception {
        // given
        COTACAO_API.stubFor(get(urlPathEqualTo("/latest"))
                .withQueryParam("base", equalTo("ARS"))
                .willReturn(okJson("{\"amount\":1.0,\"base\":\"ARS\",\"date\":\"2026-09-16\",\"rates\":{\"BRL\":0.0055}}")));

        // when
        ResultActions resposta = postJson("/api/transfers", bearer(Role.ATENDENTE), transferEm("ars", "10000.00"));

        // then
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.moedaOrigem").value("ARS"))
                .andExpect(jsonPath("$.valorBase").value(55.00));
    }
}
