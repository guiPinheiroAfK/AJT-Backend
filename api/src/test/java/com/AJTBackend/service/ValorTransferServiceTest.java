package com.AJTBackend.service;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.exception.CotacaoIndisponivelException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: a regra do valor em reais do transfer (ValorTransferService):
 * valor base informado e respeitado, BRL e sem moeda usam o valor original,
 * moeda estrangeira converte pela cotacao com duas casas (HALF_UP), api de
 * cambio fora do ar devolve null, e a moeda vai sempre em maiusculo.
 *
 * como rodar: teste unitario com o CotacaoService mockado. sem spring, sem
 * banco, sem rede. "mvn test".
 *
 * por que existe: e o numero que vira faturamento no painel. arredondamento
 * errado ou um cadastro travado porque a api de cambio caiu aparecem direto
 * no caixa da empresa.
 */
class ValorTransferServiceTest {

    private final CotacaoService cotacaoService = mock(CotacaoService.class);
    private final ValorTransferService service = new ValorTransferService(cotacaoService);

    private void cotacaoDoDolar(String taxa) {
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal(taxa), "2026-09-16"));
    }

    // ----- sem conversao -----

    @Test
    void deveRespeitarOValorBaseSemConsultarCotacao_quandoValorBaseFoiInformado() {
        // given
        BigDecimal valorBase = new BigDecimal("300.00");
        BigDecimal valorOriginal = new BigDecimal("50.00");

        // when
        BigDecimal resultado = service.calcularValorBase(valorBase, valorOriginal, "USD");

        // then
        assertThat(resultado).isEqualByComparingTo("300.00");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void deveRespeitarOValorBase_quandoNaoHaValorOriginalNemMoeda() {
        // given
        BigDecimal valorBase = new BigDecimal("99.90");

        // when
        BigDecimal resultado = service.calcularValorBase(valorBase, null, null);

        // then
        assertThat(resultado).isEqualByComparingTo("99.90");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void deveUsarOValorOriginal_quandoMoedaEhReal() {
        // given
        BigDecimal valorOriginal = new BigDecimal("120.50");

        // when
        BigDecimal resultado = service.calcularValorBase(null, valorOriginal, "BRL");

        // then
        assertThat(resultado).isEqualByComparingTo("120.50");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void deveUsarOValorOriginalComoEsta_quandoNaoHaMoeda() {
        // given
        BigDecimal valorOriginal = new BigDecimal("80.00");

        // when
        BigDecimal resultado = service.calcularValorBase(null, valorOriginal, null);

        // then
        assertThat(resultado).isEqualByComparingTo("80.00");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void deveDevolverNull_quandoNaoHaValorOriginal() {
        // when
        BigDecimal resultado = service.calcularValorBase(null, null, "USD");

        // then
        assertThat(resultado).isNull();
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    // ----- conversao de moeda estrangeira -----

    @Test
    void deveConverterParaReaisPelaCotacao_quandoMoedaEhEstrangeira() {
        // given
        cotacaoDoDolar("5.4321");

        // when
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("100.00"), "USD");

        // then
        assertThat(resultado).isEqualByComparingTo("543.21");
        verify(cotacaoService).obterCotacao("USD", "BRL");
    }

    @Test
    void deveSairComDuasCasasDecimais_quandoConverteMoedaEstrangeira() {
        // given
        cotacaoDoDolar("5.4321");

        // when
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("100.00"), "USD");

        // then
        assertThat(resultado.scale()).isEqualTo(2);
    }

    @Test
    void deveArredondarParaCima_quandoConversaoCaiNoMeio() {
        // given
        // 1,00 x 0,005 = 0,005 -> HALF_UP -> 0,01
        when(cotacaoService.obterCotacao("EUR", "BRL"))
                .thenReturn(new CotacaoDTO("EUR", "BRL", new BigDecimal("0.005"), null));

        // when
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("1.00"), "EUR");

        // then
        assertThat(resultado).isEqualByComparingTo("0.01");
    }

    @Test
    void deveConverterParaZeroComDuasCasas_quandoValorOriginalEhZero() {
        // given
        cotacaoDoDolar("5.4321");

        // when
        BigDecimal resultado = service.calcularValorBase(null, BigDecimal.ZERO, "USD");

        // then
        assertThat(resultado).isEqualByComparingTo("0.00");
        assertThat(resultado.scale()).isEqualTo(2);
    }

    // ----- api de cambio fora do ar -----

    @Test
    void deveDevolverNullSemTravarOCadastro_quandoCotacaoEstaForaDoAr() {
        // given
        when(cotacaoService.obterCotacao("EUR", "BRL"))
                .thenThrow(new CotacaoIndisponivelException("EUR", "BRL", new RuntimeException("timeout")));

        // when
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("100.00"), "EUR");

        // then
        assertThat(resultado).isNull();
        verify(cotacaoService).obterCotacao("EUR", "BRL");
    }

    // ----- normalizacao da moeda -----

    @Test
    void devePassarParaMaiusculo_quandoMoedaVemEmMinusculo() {
        // when
        String moeda = ValorTransferService.normalizarMoeda("usd");

        // then
        assertThat(moeda).isEqualTo("USD");
    }

    @Test
    void deveManterAMoeda_quandoJaEstaEmMaiusculo() {
        // when
        String moeda = ValorTransferService.normalizarMoeda("BRL");

        // then
        assertThat(moeda).isEqualTo("BRL");
    }

    @Test
    void deveContinuarNula_quandoMoedaEhNula() {
        // when
        String moeda = ValorTransferService.normalizarMoeda(null);

        // then
        assertThat(moeda).isNull();
    }
}
