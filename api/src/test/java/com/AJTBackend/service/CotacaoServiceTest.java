package com.AJTBackend.service;

import com.AJTBackend.client.CotacaoClient;
import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.CotacaoResponseDTO;
import com.AJTBackend.exception.CotacaoIndisponivelException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: a consulta de cambio (CotacaoService) — mesma moeda nao
 * chama a api externa, resposta normalizada (maiusculo) com taxa e data,
 * moeda ausente na resposta e erro do client viram CotacaoIndisponivelException.
 *
 * como rodar: teste unitario com o CotacaoClient (feign) mockado. sem
 * spring context, sem banco, sem docker, sem chamar a api real. roda com
 * "mvn test".
 *
 * por que existe: cobre a normalizacao e os erros sem depender de rede; o
 * comportamento do cache (@Cacheable) e do timeout real do feign fica pro
 * CotacaoIntegrationTest, que sobe o contexto do spring com WireMock no
 * lugar da api de verdade.
 */
class CotacaoServiceTest {

    private final CotacaoClient client = mock(CotacaoClient.class);
    private final CotacaoService service = new CotacaoService(client);

    @Test
    void mesmaMoedaRetornaTaxaUmSemChamarApi() {
        CotacaoDTO cotacao = service.obterCotacao("brl", "BRL");

        assertThat(cotacao.taxa()).isEqualByComparingTo(BigDecimal.ONE);
        verify(client, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void retornaTaxaEDataDaApiComMoedasNormalizadas() {
        when(client.obterCotacao("USD", "BRL")).thenReturn(
                new CotacaoResponseDTO(BigDecimal.ONE, "USD", "2026-09-16", Map.of("BRL", new BigDecimal("5.4321"))));

        CotacaoDTO cotacao = service.obterCotacao("usd", "brl");

        assertThat(cotacao.moedaOrigem()).isEqualTo("USD");
        assertThat(cotacao.taxa()).isEqualByComparingTo("5.4321");
        assertThat(cotacao.data()).isEqualTo("2026-09-16");
    }

    @Test
    void moedaAusenteNaRespostaViraIndisponivel() {
        when(client.obterCotacao("USD", "XYZ")).thenReturn(
                new CotacaoResponseDTO(BigDecimal.ONE, "USD", "2026-09-16", Map.of()));

        assertThatThrownBy(() -> service.obterCotacao("USD", "XYZ")).isInstanceOf(CotacaoIndisponivelException.class);
    }

    @Test
    void erroDoClientViraIndisponivel() {
        when(client.obterCotacao("USD", "BRL")).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service.obterCotacao("USD", "BRL"))
                .isInstanceOf(CotacaoIndisponivelException.class)
                .hasRootCauseMessage("timeout");
    }
}
