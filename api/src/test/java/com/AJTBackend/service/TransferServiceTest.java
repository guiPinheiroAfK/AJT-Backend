package com.AJTBackend.service;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.exception.CotacaoIndisponivelException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: a regra de calculo do valorBase do transfer (TransferService)
 * — respeita valor base explicito, usa o valor original quando ja esta em
 * BRL, converte moeda estrangeira usando a cotacao, segue em frente mesmo
 * com a api de cambio fora do ar, valida a ordem de servico referenciada e
 * recalcula certo no PATCH parcial.
 *
 * como rodar: teste unitario com mocks (CotacaoService e os repositorios
 * mockados; TransactionTemplate mockado executa o callback direto, sem
 * transacao real). sem spring context, sem banco, sem docker. roda com
 * "mvn test".
 *
 * por que existe: a conversao de moeda e a parte mais facil de acertar
 * errado (arredondamento, moeda ja em BRL, api fora do ar) e o service
 * separa a chamada http da gravacao no banco (ver o comentario na classe
 * de producao) — esse teste garante que essa separacao nao muda o resultado
 * final pro usuario.
 */
class TransferServiceTest {

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final CotacaoService cotacaoService = mock(CotacaoService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private TransferService service;

    @BeforeEach
    void setUp() {
        // executa o callback direto, sem transacao real
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });
        service = new TransferService(transferRepository, ordemServicoRepository, cotacaoService, transactionTemplate);
    }

    @Test
    void respeitaValorBaseInformadoSemConsultarCotacao() {
        TransferResponseDTO criado = service.criar(dto(new BigDecimal("300.00"), new BigDecimal("50.00"), "USD", null));

        assertThat(criado.valorBase()).isEqualByComparingTo("300.00");
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void emReaisValorBaseEhOValorOriginal() {
        TransferResponseDTO criado = service.criar(dto(null, new BigDecimal("120.50"), "brl", null));

        assertThat(criado.valorBase()).isEqualByComparingTo("120.50");
        assertThat(criado.moedaOrigem()).isEqualTo("BRL"); // normaliza pra maiusculo
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void converteMoedaEstrangeiraComDuasCasasDecimais() {
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal("5.4321"), "2026-09-16"));

        TransferResponseDTO criado = service.criar(dto(null, new BigDecimal("100.00"), "usd", null));

        assertThat(criado.valorBase()).isEqualByComparingTo("543.21");
        assertThat(criado.valorBase().scale()).isEqualTo(2);
        assertThat(criado.status()).isEqualTo(StatusTransfer.AGUARDANDO_OS);
    }

    @Test
    void cotacaoForaDoArNaoImpedeCadastro() {
        when(cotacaoService.obterCotacao("EUR", "BRL"))
                .thenThrow(new CotacaoIndisponivelException("EUR", "BRL", new RuntimeException("timeout")));

        TransferResponseDTO criado = service.criar(dto(null, new BigDecimal("100.00"), "EUR", null));

        assertThat(criado.id()).isEqualTo(10L);
        assertThat(criado.valorBase()).isNull();
    }

    @Test
    void ordemServicoInexistenteRetorna404() {
        when(ordemServicoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.criar(dto(null, null, null, 99L)))
                .isInstanceOf(OrdemServicoNaoEncontradoException.class);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void atualizacaoParcialRecalculaComMoedaJaSalva() {
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").valorOriginal(new BigDecimal("10.00")).moedaOrigem("USD")
                .valorBase(new BigDecimal("50.00")).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal("6"), null));

        TransferRequestDTO patch = new TransferRequestDTO(null, null, null, null, null,
                null, new BigDecimal("20.00"), null, null);
        TransferResponseDTO atualizado = service.atualizarParcial(5L, patch);

        assertThat(atualizado.valorOriginal()).isEqualByComparingTo("20.00");
        assertThat(atualizado.valorBase()).isEqualByComparingTo("120.00");
        assertThat(atualizado.origem()).isEqualTo("A"); // campos nao enviados ficam intactos
    }

    @Test
    void atualizacaoParcialSoDeStatusNaoConsultaCotacao() {
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));

        TransferRequestDTO patch = new TransferRequestDTO(null, null, null, null, StatusTransfer.CONFIRMADO,
                null, null, null, null);
        assertThat(service.atualizarParcial(5L, patch).status()).isEqualTo(StatusTransfer.CONFIRMADO);
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
    }

    @Test
    void atualizarInexistenteRetorna404() {
        when(transferRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(1L, dto(null, null, null, null)))
                .isInstanceOf(TransferNaoEncontradoException.class);
    }

    private static TransferRequestDTO dto(BigDecimal valorBase, BigDecimal valorOriginal, String moeda, Long osId) {
        return new TransferRequestDTO(LocalDate.of(2026, 9, 20), LocalTime.of(14, 30), "Aeroporto GRU", "Hotel",
                null, valorBase, valorOriginal, moeda, osId);
    }
}
