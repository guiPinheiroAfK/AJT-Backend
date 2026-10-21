package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.PainelResumoDTO;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: o resumo do painel (PainelService) — conta transfers e OS do dia
 * por status (com 0 nos status sem registro), soma os totais, e so calcula o
 * faturamento (dia e mes, sem cancelados) pra ADMIN e GERENTE.
 *
 * como rodar: teste unitario com os repositorios mockados e o perfil simulado
 * no SecurityContextHolder. sem spring, sem banco. roda com "mvn test".
 *
 * por que existe: o painel e a primeira tela depois do login. se o faturamento
 * vazar pra atendente/motorista ou um status sumir do mapa, ninguem percebe
 * olhando o codigo.
 */
class PainelServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 19);

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

    @Test
    void contaPorStatusDoDiaComZeroNosQueNaoVieram() {
        logarComo("ATENDENTE");
        when(transferRepository.contarPorStatusNaData(DIA)).thenReturn(List.of(
                new Object[]{StatusTransfer.AGUARDANDO_OS, 3L},
                new Object[]{StatusTransfer.CONFIRMADO, 2L}));
        when(ordemServicoRepository.contarPorStatusNaData(DIA)).thenReturn(List.<Object[]>of(
                new Object[]{StatusOrdemServico.ABERTA, 1L}));

        PainelResumoDTO resumo = service.resumo(DIA);

        assertThat(resumo.data()).isEqualTo(DIA);
        assertThat(resumo.totalTransfers()).isEqualTo(5);
        assertThat(resumo.transfersPorStatus())
                .containsEntry(StatusTransfer.AGUARDANDO_OS, 3L)
                .containsEntry(StatusTransfer.CANCELADO, 0L)
                .hasSize(StatusTransfer.values().length);
        assertThat(resumo.totalOrdensServico()).isEqualTo(1);
        assertThat(resumo.ordensServicoPorStatus())
                .containsEntry(StatusOrdemServico.ABERTA, 1L)
                .containsEntry(StatusOrdemServico.EM_ANDAMENTO, 0L);
    }

    @Test
    void faturamentoNaoApareceParaAtendenteNemMotorista() {
        logarComo("MOTORISTA");

        PainelResumoDTO resumo = service.resumo(DIA);

        assertThat(resumo.faturamentoDia()).isNull();
        assertThat(resumo.faturamentoMes()).isNull();
        verify(transferRepository, never()).somarValorBaseNoPeriodo(any(), any(), any());
    }

    @Test
    void gerenteVeFaturamentoDoDiaEDoMesSemCancelados() {
        logarComo("GERENTE");
        when(transferRepository.somarValorBaseNoPeriodo(DIA, DIA, StatusTransfer.CANCELADO))
                .thenReturn(new BigDecimal("450.00"));
        when(transferRepository.somarValorBaseNoPeriodo(LocalDate.of(2026, 10, 1), DIA, StatusTransfer.CANCELADO))
                .thenReturn(new BigDecimal("9800.00"));

        PainelResumoDTO resumo = service.resumo(DIA);

        assertThat(resumo.faturamentoDia()).isEqualByComparingTo("450.00");
        assertThat(resumo.faturamentoMes()).isEqualByComparingTo("9800.00");
    }

    @Test
    void semDataUsaHojeNoFusoDeSaoPaulo() {
        logarComo("ADMIN");

        PainelResumoDTO resumo = service.resumo(null);

        assertThat(resumo.data()).isEqualTo(LocalDate.now(PainelService.FUSO_OPERACAO));
    }
}
