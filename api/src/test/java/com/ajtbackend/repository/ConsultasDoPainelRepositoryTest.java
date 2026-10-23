package com.ajtbackend.repository;

import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.Transfer;
import com.ajtbackend.model.enums.StatusOrdemServico;
import com.ajtbackend.model.enums.StatusTransfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: as consultas agregadas do painel (HU08) no banco real — contagem de transfers
 * e de OS por status num dia (GROUP BY) e o faturamento de um periodo, que soma o valor base
 * sem os cancelados e devolve zero (e nao null) quando nao ha nada no periodo.
 *
 * como rodar: @DataJpaTest com PostgreSQL via Testcontainers (RepositorioTestBase).
 * precisa de Docker; sem Docker a classe e pulada.
 *
 * por que existe: o PainelServiceTest usa mocks dessas consultas; aqui se prova que o JPQL
 * agrupa, filtra e soma do jeito que o service espera.
 */
class ConsultasDoPainelRepositoryTest extends RepositorioTestBase {

    private static final LocalDate DIA = LocalDate.of(2031, 5, 20);

    @Autowired private TestEntityManager em;
    @Autowired private TransferRepository transferRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;

    private void transfer(LocalDate data, StatusTransfer status, String valor) {
        em.persist(Transfer.builder().dataTransfer(data).horaTransfer(LocalTime.NOON).origem("GRU").destino("Hotel")
                .status(status).valorBase(valor != null ? new BigDecimal(valor) : null).build());
    }

    private static <E> Map<E, Long> porStatus(List<Object[]> linhas) {
        return linhas.stream().collect(Collectors.toMap(l -> {
            @SuppressWarnings("unchecked") E status = (E) l[0];
            return status;
        }, l -> (Long) l[1]));
    }

    @Test
    void deveContarTransfersDoDiaPorStatus_quandoDiaTemStatusDiferentesEOutroDiaTemTransfer() {
        // given
        transfer(DIA, StatusTransfer.CONFIRMADO, "100");
        transfer(DIA, StatusTransfer.CONFIRMADO, "50");
        transfer(DIA, StatusTransfer.CANCELADO, "80");
        transfer(DIA.plusDays(1), StatusTransfer.CONFIRMADO, "70");
        em.flush();

        // when
        Map<StatusTransfer, Long> contagem = porStatus(transferRepository.contarPorStatusNaData(DIA));

        // then
        assertThat(contagem).containsOnly(
                Map.entry(StatusTransfer.CONFIRMADO, 2L),
                Map.entry(StatusTransfer.CANCELADO, 1L));
    }

    @Test
    void naoDeveContarNenhumTransfer_quandoDiaNaoTemTransfer() {
        // given
        transfer(DIA.plusDays(5), StatusTransfer.CONFIRMADO, "10");
        em.flush();

        // when
        List<Object[]> linhas = transferRepository.contarPorStatusNaData(DIA);

        // then
        assertThat(linhas).isEmpty();
    }

    @Test
    void deveContarOsDoDiaPorStatus_quandoDiaTemStatusDiferentesEOutroDiaTemOs() {
        // given
        em.persist(OrdemServico.builder().dataServico(DIA).status(StatusOrdemServico.ABERTA).build());
        em.persist(OrdemServico.builder().dataServico(DIA).status(StatusOrdemServico.FINALIZADA).build());
        em.persist(OrdemServico.builder().dataServico(DIA.minusDays(1)).status(StatusOrdemServico.ABERTA).build());
        em.flush();

        // when
        Map<StatusOrdemServico, Long> contagem = porStatus(ordemServicoRepository.contarPorStatusNaData(DIA));

        // then
        assertThat(contagem).containsOnly(
                Map.entry(StatusOrdemServico.ABERTA, 1L),
                Map.entry(StatusOrdemServico.FINALIZADA, 1L));
    }

    @Test
    void naoDeveContarNenhumaOs_quandoDiaNaoTemOs() {
        // given
        em.persist(OrdemServico.builder().dataServico(DIA.plusDays(3)).status(StatusOrdemServico.ABERTA).build());
        em.flush();

        // when
        List<Object[]> linhas = ordemServicoRepository.contarPorStatusNaData(DIA);

        // then
        assertThat(linhas).isEmpty();
    }

    @Test
    void deveSomarSoOsValidosDoPeriodo_quandoHaCanceladoValorVazioETransferForaDoPeriodo() {
        // given
        transfer(DIA, StatusTransfer.CONFIRMADO, "100.50");
        transfer(DIA.plusDays(3), StatusTransfer.CONCLUIDO, "49.50");
        transfer(DIA, StatusTransfer.CANCELADO, "1000");
        transfer(DIA, StatusTransfer.AGUARDANDO_OS, null);
        transfer(DIA.plusDays(30), StatusTransfer.CONFIRMADO, "999");
        em.flush();

        // when
        BigDecimal faturamento = transferRepository.somarValorBaseNoPeriodo(DIA, DIA.plusDays(7), StatusTransfer.CANCELADO);

        // then
        assertThat(faturamento).isEqualByComparingTo("150.00");
    }

    @Test
    void deveFaturarZeroENaoNull_quandoPeriodoNaoTemTransfer() {
        // when
        BigDecimal faturamento = transferRepository.somarValorBaseNoPeriodo(
                DIA.plusYears(1), DIA.plusYears(1).plusDays(7), StatusTransfer.CANCELADO);

        // then
        assertThat(faturamento).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
