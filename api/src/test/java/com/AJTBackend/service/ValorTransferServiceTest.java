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
 * o que testa: a regra que decide o valorBase (em reais) do transfer
 * (ValorTransferService) — respeita o valorBase informado, usa o valor
 * original quando ja esta em BRL, converte moeda estrangeira com a cotacao
 * (2 casas, arredondamento HALF_UP), devolve null se a api de cambio estiver
 * fora do ar e normaliza o codigo da moeda pra maiusculo.
 *
 * como rodar: teste unitario com o CotacaoService mockado. sem spring, sem
 * banco, sem docker, sem chamar a api real. roda com "mvn test".
 *
 * por que existe: e a parte da regra de negocio mais facil de errar
 * (arredondamento, moeda ja em BRL, dependencia externa fora do ar) e agora
 * vive isolada do TransferService, entao pode ser testada sem montar
 * repositorios nem transacao.
 */
class ValorTransferServiceTest {

    private final CotacaoService cotacaoService = mock(CotacaoService.class);
    private final ValorTransferService service = new ValorTransferService(cotacaoService);

    @Test
    void respeitaValorBaseInformadoSemConsultarCotacao() {
        BigDecimal resultado = service.calcularValorBase(new BigDecimal("300.00"), new BigDecimal("50.00"), "USD");

        assertThat(resultado).isEqualByComparingTo("300.00");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void emReaisOValorBaseEhOValorOriginal() {
        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("120.50"), "BRL");

        assertThat(resultado).isEqualByComparingTo("120.50");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void semMoedaOValorOriginalEhUsadoComoEsta() {
        assertThat(service.calcularValorBase(null, new BigDecimal("80.00"), null)).isEqualByComparingTo("80.00");
    }

    @Test
    void semValorOriginalNaoHaOQueCalcular() {
        assertThat(service.calcularValorBase(null, null, "USD")).isNull();
    }

    @Test
    void converteMoedaEstrangeiraComDuasCasasDecimais() {
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal("5.4321"), "2026-09-16"));

        BigDecimal resultado = service.calcularValorBase(null, new BigDecimal("100.00"), "USD");

        assertThat(resultado).isEqualByComparingTo("543.21");
        assertThat(resultado.scale()).isEqualTo(2);
    }

    @Test
    void arredondaMeioParaCima() {
        when(cotacaoService.obterCotacao("EUR", "BRL"))
                .thenReturn(new CotacaoDTO("EUR", "BRL", new BigDecimal("0.005"), null));

        // 1,00 x 0,005 = 0,005 -> HALF_UP -> 0,01
        assertThat(service.calcularValorBase(null, new BigDecimal("1.00"), "EUR")).isEqualByComparingTo("0.01");
    }

    @Test
    void cotacaoForaDoArNaoTravaOCadastro() {
        when(cotacaoService.obterCotacao("EUR", "BRL"))
                .thenThrow(new CotacaoIndisponivelException("EUR", "BRL", new RuntimeException("timeout")));

        assertThat(service.calcularValorBase(null, new BigDecimal("100.00"), "EUR")).isNull();
    }

    @Test
    void normalizaCodigoDaMoeda() {
        assertThat(ValorTransferService.normalizarMoeda("usd")).isEqualTo("USD");
        assertThat(ValorTransferService.normalizarMoeda("BRL")).isEqualTo("BRL");
        assertThat(ValorTransferService.normalizarMoeda(null)).isNull();
    }
}
