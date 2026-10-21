package com.AJTBackend.service;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.FiltroTransferDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.PassageiroRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: o restante das regras do TransferService (o TransferServiceTest cobre cambio,
 * passageiros e o vinculo basico com a OS) — id inexistente em cada operacao (404), periodo
 * invertido no filtro (400), PATCH campo a campo inclusive trocando so a moeda (recalcula com
 * o valor ja salvo), PUT com lista de passageiros, as bordas da regra da OS (sair da OS sem
 * estar confirmado, mudar so a data dentro da mesma OS, OS finalizada) e a exclusao auditada.
 *
 * como rodar: teste unitario com repositorios mockados e o TransactionTemplate executando o
 * callback direto. sem spring, sem banco. "mvn test".
 *
 * por que existe: o transfer e o que vira dinheiro (faturamento) e rota (OS); cada borda
 * dessa regra ja foi bug em algum momento do projeto.
 */
class TransferServiceRegrasTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 21);

    private final TransferRepository repository = mock(TransferRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final PassageiroRepository passageiroRepository = mock(PassageiroRepository.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final CotacaoService cotacaoService = mock(CotacaoService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private TransferService service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        service = new TransferService(repository, ordemServicoRepository, passageiroRepository,
                new ValorTransferService(cotacaoService), auditoriaService, transactionTemplate);
    }

    private static Transfer transfer(StatusTransfer status, OrdemServico os) {
        return Transfer.builder().id(5L).dataTransfer(DIA).horaTransfer(LocalTime.NOON).origem("A").destino("B")
                .status(status).ordemServico(os).valorOriginal(new BigDecimal("10.00")).moedaOrigem("USD").build();
    }

    private static OrdemServico os(Long id, StatusOrdemServico status) {
        return OrdemServico.builder().id(id).dataServico(DIA).status(status).build();
    }

    private static TransferRequestDTO patch(LocalDate data, LocalTime hora, String origem, String moeda, Long osId) {
        return new TransferRequestDTO(data, hora, origem, null, null, null, null, moeda, osId, null, null);
    }

    @Test
    void idInexistenteLanca404EmCadaOperacao() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(TransferNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizar(9L, patch(DIA, LocalTime.NOON, "A", null, null)))
                .isInstanceOf(TransferNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(9L, patch(null, null, "C", null, null)))
                .isInstanceOf(TransferNaoEncontradoException.class);
        // trocando a moeda o 404 sai antes, na leitura pra recalcular o valor
        assertThatThrownBy(() -> service.atualizarParcial(9L, patch(null, null, null, "EUR", null)))
                .isInstanceOf(TransferNaoEncontradoException.class);
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(TransferNaoEncontradoException.class);
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void periodoInvertidoNoFiltroLanca400EPeriodoCertoConsulta() {
        assertThatThrownBy(() -> service.listar(new FiltroTransferDTO(null, DIA, DIA.minusDays(1), null, null, null), PageRequest.of(0, 5)))
                .isInstanceOf(RegraNegocioException.class);

        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(transfer(StatusTransfer.CONFIRMADO, null))));
        assertThat(service.listar(new FiltroTransferDTO(StatusTransfer.CONFIRMADO, DIA, DIA, "a", 1L, true), PageRequest.of(0, 5)).conteudo())
                .hasSize(1);
    }

    @Test
    void patchCampoACampoETrocandoSoAMoedaRecalculaComOValorSalvo() {
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.AGUARDANDO_OS, null)));
        when(cotacaoService.obterCotacao("EUR", "BRL")).thenReturn(new CotacaoDTO("EUR", "BRL", new BigDecimal("6.5"), null));

        var salvo = service.atualizarParcial(5L, patch(DIA.plusDays(1), LocalTime.MIDNIGHT, "Rodoviaria", "eur", null));

        assertThat(salvo.dataTransfer()).isEqualTo(DIA.plusDays(1));
        assertThat(salvo.horaTransfer()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(salvo.origem()).isEqualTo("Rodoviaria");
        assertThat(salvo.moedaOrigem()).isEqualTo("EUR");
        assertThat(salvo.valorBase()).isEqualByComparingTo("65.00"); // 10 (ja salvo) x 6,5
    }

    @Test
    void putComPassageirosTrocaALista() {
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.AGUARDANDO_OS, null)));
        when(passageiroRepository.findAllById(Set.of(1L))).thenReturn(List.of(Passageiro.builder().id(1L).nome("Ana").build()));

        var put = new TransferRequestDTO(DIA, LocalTime.NOON, "A", "B", null, new BigDecimal("50"), null, "BRL", null, Set.of(1L), null);

        assertThat(service.atualizar(5L, put).passageiroIds()).containsExactly(1L);
    }

    @Test
    void sairDaOsEmAndamentoNaoVoltaPraAguardandoOs() {
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.EM_ANDAMENTO, os(3L, StatusOrdemServico.EM_ANDAMENTO))));

        var put = new TransferRequestDTO(DIA, LocalTime.NOON, "A", "B", StatusTransfer.EM_ANDAMENTO, new BigDecimal("1"), null, "BRL", null, null, null);

        assertThat(service.atualizar(5L, put).status()).isEqualTo(StatusTransfer.EM_ANDAMENTO); // so CONFIRMADO volta
    }

    @Test
    void mudarSoADataDentroDaMesmaOsTambemConfereODia() {
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os(3L, StatusOrdemServico.ABERTA))));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os(3L, StatusOrdemServico.ABERTA)));

        assertThatThrownBy(() -> service.atualizarParcial(5L, patch(DIA.plusDays(1), null, null, null, 3L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("mesmo dia");
    }

    @Test
    void naoEntraEmOsFinalizadaMasTrocarDeUmaOsAbertaPraOutraPode() {
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os(3L, StatusOrdemServico.ABERTA))));
        when(ordemServicoRepository.findById(4L)).thenReturn(Optional.of(os(4L, StatusOrdemServico.FINALIZADA)));
        when(ordemServicoRepository.findById(6L)).thenReturn(Optional.of(os(6L, StatusOrdemServico.EM_ANDAMENTO)));

        assertThatThrownBy(() -> service.atualizarParcial(5L, patch(null, null, null, null, 4L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("FINALIZADA");
        assertThat(service.atualizarParcial(5L, patch(null, null, null, null, 6L)).osId()).isEqualTo(6L);
    }

    @Test
    void exclusaoApagaEAudita() {
        when(repository.existsById(5L)).thenReturn(true);

        service.deletar(5L);

        verify(repository).deleteById(5L);
        verify(auditoriaService).registrar("transfers", 5L, "Transfer removido");
    }
}
