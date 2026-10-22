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

class ValorTransferServiceTest {

    private final CotacaoService cotacaoService = mock(CotacaoService.class);
    private final ValorTransferService service = new ValorTransferService(cotacaoService);

    // ----- sem conversao -----

    @Test
    void respeitaValorBaseInformadoSemConsultarCotacao() {
        // Given
        BigDecimal valorBase = new BigDecimal("300.00");
        BigDecimal valorOriginal = new BigDecimal("50.00");

        // When
        BigDecimal resultado = service.calcularValorBase(valorBase, valorOriginal, "USD");

        // Then
        assertThat(resultado).isEqualByComparingTo("300.00");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void respeitaValorBaseInformadoMesmoSemValorOriginalNemMoeda() {
        // Given
        BigDecimal valorBase = new BigDecimal("99.90");

        // When
        BigDecimal resultado = service.calcularValorBase(valorBase, null, null);

        // Then
        assertThat(resultado).isEqualByComparingTo("99.90");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void emReaisOValorBaseEhOValorOriginal() {
        // Given
        BigDecimal valorOriginal = new BigDecimal("120.50");

        // When
        BigDecimal resultado = service.calcularValorBase(null, valorOriginal, "BRL");

        // Then
        assertThat(resultado).isEqualByComparingTo("120.50");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void semMoedaOValorOriginalEhUsadoComoEsta() {
        // Given
        BigDecimal valorOriginal = new BigDecimal("80.00");

        // When
        BigDecimal resultado = service.calcularValorBase(null, valorOriginal, null);

        // Then
        assertThat(resultado).isEqualByComparingTo("80.00");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void semValorOriginalNaoHaOQueCalcular() {
        // When
        BigDecimal resultado = service.calcularValorBase(null, null, "USD");

        // Then
        assertThat(resultado).isNull();
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    // ----- conversao de moeda estrangeira -----

    @Test
    void converteMoedaEstrangeiraPelaCotacaoParaReais() {
        // Given
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal("5.4321"), "2026-09-16"));

        // When
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("100.00"), "USD");

        // Then
        assertThat(resultado).isEqualByComparingTo("543.21");
        verify(cotacaoService).obterCotacao("USD", "BRL");
    }

    @Test
    void valorConvertidoSaiComExatamenteDuasCasasDecimais() {
        // Given
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal("5.4321"), "2026-09-16"));

        // When
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("100.00"), "USD");

        // Then
        assertThat(resultado.scale()).isEqualTo(2);
    }

    @Test
    void arredondaMeioParaCima() {
        // Given: 1,00 x 0,005 = 0,005 -> HALF_UP -> 0,01
        when(cotacaoService.obterCotacao("EUR", "BRL"))
                .thenReturn(new CotacaoDTO("EUR", "BRL", new BigDecimal("0.005"), null));

        // When
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("1.00"), "EUR");

        // Then
        assertThat(resultado).isEqualByComparingTo("0.01");
    }

    @Test
    void valorOriginalZeroConverteParaZeroComDuasCasas() {
        // Given
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal("5.4321"), null));

        // When
        BigDecimal resultado = service.calcularValorBase(null, BigDecimal.ZERO, "USD");

        // Then
        assertThat(resultado).isEqualByComparingTo("0.00");
        assertThat(resultado.scale()).isEqualTo(2);
    }

    // ----- api de cambio fora do ar -----

    @Test
    void cotacaoForaDoArNaoTravaOCadastroEDevolveNull() {
        // Given
        when(cotacaoService.obterCotacao("EUR", "BRL"))
                .thenThrow(new CotacaoIndisponivelException("EUR", "BRL", new RuntimeException("timeout")));

        // When
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("100.00"), "EUR");

        // Then
        assertThat(resultado).isNull();
        verify(cotacaoService).obterCotacao("EUR", "BRL");
    }

    // ----- normalizacao da moeda -----

    @Test
    void normalizarMoedaPassaMinusculasParaMaiusculas() {
        // When
        String moeda = ValorTransferService.normalizarMoeda("usd");

        // Then
        assertThat(moeda).isEqualTo("USD");
    }

    @Test
    void normalizarMoedaMantemOQueJaEstaEmMaiusculo() {
        // When
        String moeda = ValorTransferService.normalizarMoeda("BRL");

        // Then
        assertThat(moeda).isEqualTo("BRL");
    }

    @Test
    void normalizarMoedaNulaContinuaNula() {
        // When
        String moeda = ValorTransferService.normalizarMoeda(null);

        // Then
        assertThat(moeda).isNull();
    }
}