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

/*
 * o que testa: o resumo do painel (PainelService): contagem de transfers e
 * OS do dia por status (com zero nos que nao vieram), totais, faturamento do
 * dia e do mes so para ADMIN e GERENTE, e a data padrao no fuso de sao paulo.
 *
 * como rodar: teste unitario com os repositorios mockados e o perfil simulado
 * no SecurityContextHolder. sem spring, sem banco. "mvn test".
 *
 * por que existe: o faturamento e informacao gerencial; se a regra de perfil
 * falhar, atendente e motorista passam a ver o caixa da empresa. as queries
 * de verdade (contagem e soma sem cancelado) ficam no ConsultasDoPainelRepositoryTest.
 */
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
    void deveContarTransfersPorStatus_quandoDiaTemTransfers() {
        // given
        logarComo("ATENDENTE");
        transfersDoDia(
                new Object[]{StatusTransfer.AGUARDANDO_OS, 3L},
                new Object[]{StatusTransfer.CONFIRMADO, 2L});

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.data()).isEqualTo(DIA);
        assertThat(resumo.transfersPorStatus())
                .containsEntry(StatusTransfer.AGUARDANDO_OS, 3L)
                .containsEntry(StatusTransfer.CONFIRMADO, 2L);
    }

    @Test
    void deveMostrarZero_quandoStatusDeTransferNaoTemRegistro() {
        // given
        logarComo("ATENDENTE");
        transfersDoDia(new Object[]{StatusTransfer.AGUARDANDO_OS, 3L});

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.transfersPorStatus())
                .containsEntry(StatusTransfer.CANCELADO, 0L)
                .hasSize(StatusTransfer.values().length);
    }

    @Test
    void deveSomarOsStatus_quandoCalculaTotalDeTransfers() {
        // given
        logarComo("ATENDENTE");
        transfersDoDia(
                new Object[]{StatusTransfer.AGUARDANDO_OS, 3L},
                new Object[]{StatusTransfer.CONFIRMADO, 2L});

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.totalTransfers()).isEqualTo(5);
    }

    @Test
    void deveContarOsPorStatusComZeroNosQueFaltam_quandoDiaTemOs() {
        // given
        logarComo("ATENDENTE");
        ordensDoDia(new Object[]{StatusOrdemServico.ABERTA, 1L});

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.ordensServicoPorStatus())
                .containsEntry(StatusOrdemServico.ABERTA, 1L)
                .containsEntry(StatusOrdemServico.EM_ANDAMENTO, 0L)
                .hasSize(StatusOrdemServico.values().length);
    }

    @Test
    void deveSomarOsStatus_quandoCalculaTotalDeOs() {
        // given
        logarComo("ATENDENTE");
        ordensDoDia(
                new Object[]{StatusOrdemServico.ABERTA, 1L},
                new Object[]{StatusOrdemServico.EM_ANDAMENTO, 4L});

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.totalOrdensServico()).isEqualTo(5);
    }

    @Test
    void deveZerarTotaisEStatus_quandoDiaNaoTemNenhumRegistro() {
        // given
        logarComo("ATENDENTE");

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.totalTransfers()).isZero();
        assertThat(resumo.totalOrdensServico()).isZero();
        assertThat(resumo.transfersPorStatus().values()).containsOnly(0L);
        assertThat(resumo.ordensServicoPorStatus().values()).containsOnly(0L);
    }

    @Test
    void deveConverterParaLong_quandoBancoDevolveContagemComoInteger() {
        // given
        // o driver pode devolver o COUNT como Integer em vez de Long
        logarComo("ATENDENTE");
        transfersDoDia(new Object[]{StatusTransfer.CONFIRMADO, 4});

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.transfersPorStatus()).containsEntry(StatusTransfer.CONFIRMADO, 4L);
        assertThat(resumo.totalTransfers()).isEqualTo(4);
    }

    // ----- faturamento por perfil -----

    @ParameterizedTest(name = "{0} nao ve faturamento")
    @ValueSource(strings = {"ATENDENTE", "MOTORISTA"})
    void naoDeveMostrarNemConsultarFaturamento_quandoPerfilNaoGerencia(String perfil) {
        // given
        logarComo(perfil);

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.faturamentoDia()).isNull();
        assertThat(resumo.faturamentoMes()).isNull();
        verify(transferRepository, never()).somarValorBaseNoPeriodo(any(), any(), any());
    }

    @ParameterizedTest(name = "{0} ve faturamento do dia e do mes")
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void deveMostrarFaturamentoDoDiaEDoMesSemCancelados_quandoPerfilGerencia(String perfil) {
        // given
        logarComo(perfil);
        when(transferRepository.somarValorBaseNoPeriodo(DIA, DIA, StatusTransfer.CANCELADO))
                .thenReturn(new BigDecimal("450.00"));
        when(transferRepository.somarValorBaseNoPeriodo(PRIMEIRO_DO_MES, DIA, StatusTransfer.CANCELADO))
                .thenReturn(new BigDecimal("9800.00"));

        // when
        PainelResumoDTO resumo = service.resumo(DIA);

        // then
        assertThat(resumo.faturamentoDia()).isEqualByComparingTo("450.00");
        assertThat(resumo.faturamentoMes()).isEqualByComparingTo("9800.00");
    }

    @Test
    void deveUsarPeriodoDeUmDiaSoNoMes_quandoDataEhODia1() {
        // given
        logarComo("GERENTE");
        LocalDate primeiroDeNovembro = LocalDate.of(2026, 11, 1);

        // when
        service.resumo(primeiroDeNovembro);

        // then
        // no dia 1 o periodo do mes e um dia so: as duas somas usam o mesmo intervalo
        verify(transferRepository, times(2))
                .somarValorBaseNoPeriodo(primeiroDeNovembro, primeiroDeNovembro, StatusTransfer.CANCELADO);
    }

    // ----- data da consulta -----

    @Test
    void deveConsultarAsContagensNaData_quandoDataEhInformada() {
        // given
        logarComo("ATENDENTE");

        // when
        service.resumo(DIA);

        // then
        verify(transferRepository).contarPorStatusNaData(DIA);
        verify(ordemServicoRepository).contarPorStatusNaData(DIA);
    }

    @Test
    void deveUsarHojeNoFusoDeSaoPaulo_quandoDataNaoEhInformada() {
        // given
        logarComo("ADMIN");
        LocalDate antes = LocalDate.now(PainelService.FUSO_OPERACAO);

        // when
        PainelResumoDTO resumo = service.resumo(null);

        // then
        // aceita virar o dia entre a chamada e a conferencia
        LocalDate depois = LocalDate.now(PainelService.FUSO_OPERACAO);
        assertThat(resumo.data()).isBetween(antes, depois);
        ArgumentCaptor<LocalDate> consultada = ArgumentCaptor.forClass(LocalDate.class);
        verify(transferRepository).contarPorStatusNaData(consultada.capture());
        assertThat(consultada.getValue()).isEqualTo(resumo.data());
    }
}
