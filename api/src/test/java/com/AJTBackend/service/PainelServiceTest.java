package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.PainelResumoDTO;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PainelServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 19);
    private static final LocalDate PRIMEIRO_DO_MES = LocalDate.of(2026, 10, 1);

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final PainelService service = new PainelService(transferRepository, ordemServicoRepository, new UsuarioLogado());

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void logarComo(String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    private void transfersDoDia(Object[]... linhas) {
        when(transferRepository.contarPorStatusNaData(DIA)).thenReturn(List.of(linhas));
    }

    private void ordensDoDia(Object[]... linhas) {
        when(ordemServicoRepository.contarPorStatusNaData(DIA)).thenReturn(List.of(linhas));
    }

    // ----- contagem por status -----

    @Test
    void transfersDoDiaSaemContadosPorStatus() {
        // Given
        logarComo("ATENDENTE");
        transfersDoDia(
                new Object[]{StatusTransfer.AGUARDANDO_OS, 3L},
                new Object[]{StatusTransfer.CONFIRMADO, 2L});

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.data()).isEqualTo(DIA);
        assertThat(resumo.transfersPorStatus())
                .containsEntry(StatusTransfer.AGUARDANDO_OS, 3L)
                .containsEntry(StatusTransfer.CONFIRMADO, 2L);
    }

    @Test
    void statusDeTransferSemRegistroApareceComZero() {
        // Given
        logarComo("ATENDENTE");
        transfersDoDia(new Object[]{StatusTransfer.AGUARDANDO_OS, 3L});

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.transfersPorStatus())
                .containsEntry(StatusTransfer.CANCELADO, 0L)
                .hasSize(StatusTransfer.values().length);
    }

    @Test
    void totalDeTransfersEhASomaDosStatus() {
        // Given
        logarComo("ATENDENTE");
        transfersDoDia(
                new Object[]{StatusTransfer.AGUARDANDO_OS, 3L},
                new Object[]{StatusTransfer.CONFIRMADO, 2L});

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.totalTransfers()).isEqualTo(5);
    }

    @Test
    void ordensDeServicoDoDiaSaemContadasPorStatusComZeroNosQueNaoVieram() {
        // Given
        logarComo("ATENDENTE");
        ordensDoDia(new Object[]{StatusOrdemServico.ABERTA, 1L});

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.ordensServicoPorStatus())
                .containsEntry(StatusOrdemServico.ABERTA, 1L)
                .containsEntry(StatusOrdemServico.EM_ANDAMENTO, 0L)
                .hasSize(StatusOrdemServico.values().length);
    }

    @Test
    void totalDeOrdensDeServicoEhASomaDosStatus() {
        // Given
        logarComo("ATENDENTE");
        ordensDoDia(
                new Object[]{StatusOrdemServico.ABERTA, 1L},
                new Object[]{StatusOrdemServico.EM_ANDAMENTO, 4L});

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.totalOrdensServico()).isEqualTo(5);
    }

    @Test
    void diaSemNenhumRegistroTemTotaisZeradosEOsStatusTodosComZero() {
        // Given
        logarComo("ATENDENTE");

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.totalTransfers()).isZero();
        assertThat(resumo.totalOrdensServico()).isZero();
        assertThat(resumo.transfersPorStatus().values()).containsOnly(0L);
        assertThat(resumo.ordensServicoPorStatus().values()).containsOnly(0L);
    }

    @Test
    void contagemQueVemDoBancoComoIntegerTambemEhConvertida() {
        // Given: o driver pode devolver o COUNT como Integer em vez de Long
        logarComo("ATENDENTE");
        transfersDoDia(new Object[]{StatusTransfer.CONFIRMADO, 4});

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.transfersPorStatus()).containsEntry(StatusTransfer.CONFIRMADO, 4L);
        assertThat(resumo.totalTransfers()).isEqualTo(4);
    }

    // ----- faturamento por perfil -----

    @ParameterizedTest
    @ValueSource(strings = {"ATENDENTE", "MOTORISTA"})
    void faturamentoNaoApareceParaQuemNaoGerencia(String perfil) {
        // Given
        logarComo(perfil);

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.faturamentoDia()).isNull();
        assertThat(resumo.faturamentoMes()).isNull();
        verify(transferRepository, never()).somarValorBaseNoPeriodo(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void adminEGerenteVeemOFaturamentoDoDiaEDoMesSemCancelados(String perfil) {
        // Given
        logarComo(perfil);
        when(transferRepository.somarValorBaseNoPeriodo(DIA, DIA, StatusTransfer.CANCELADO))
                .thenReturn(new BigDecimal("450.00"));
        when(transferRepository.somarValorBaseNoPeriodo(PRIMEIRO_DO_MES, DIA, StatusTransfer.CANCELADO))
                .thenReturn(new BigDecimal("9800.00"));

        // When
        PainelResumoDTO resumo = service.resumo(DIA);

        // Then
        assertThat(resumo.faturamentoDia()).isEqualByComparingTo("450.00");
        assertThat(resumo.faturamentoMes()).isEqualByComparingTo("9800.00");
    }

    @Test
    void faturamentoDoMesVaiDoPrimeiroDiaDoMesAteADataConsultada() {
        // Given
        logarComo("GERENTE");
        LocalDate primeiroDeNovembro = LocalDate.of(2026, 11, 1);

        // When
        service.resumo(primeiroDeNovembro);

        // Then: no dia 1 o periodo do mes e um dia so
        verify(transferRepository, times(2))
                .somarValorBaseNoPeriodo(primeiroDeNovembro, primeiroDeNovembro, StatusTransfer.CANCELADO);
    }

    // ----- data da consulta -----

    @Test
    void dataInformadaEAUsadaNasConsultasDeContagem() {
        // Given
        logarComo("ATENDENTE");

        // When
        service.resumo(DIA);

        // Then
        verify(transferRepository).contarPorStatusNaData(DIA);
        verify(ordemServicoRepository).contarPorStatusNaData(DIA);
    }

    @Test
    void semDataUsaHojeNoFusoDeSaoPaulo() {
        // Given
        logarComo("ADMIN");
        LocalDate antes = LocalDate.now(PainelService.FUSO_OPERACAO);

        // When
        PainelResumoDTO resumo = service.resumo(null);

        // Then: aceita virar o dia entre a chamada e a conferencia
        LocalDate depois = LocalDate.now(PainelService.FUSO_OPERACAO);
        assertThat(resumo.data()).isBetween(antes, depois);
        ArgumentCaptor<LocalDate> consultada = ArgumentCaptor.forClass(LocalDate.class);
        verify(transferRepository).contarPorStatusNaData(consultada.capture());
        assertThat(consultada.getValue()).isEqualTo(resumo.data());
    }
}