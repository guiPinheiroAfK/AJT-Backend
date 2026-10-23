package com.AJTBackend.service;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.FiltroTransferDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static FiltroTransferDTO filtroSemOs(Boolean semOs) {
        return new FiltroTransferDTO(null, null, null, null, null, semOs);
    }

    @SuppressWarnings("unchecked")
    private void repositorioDevolveUmTransfer() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transfer(StatusTransfer.CONFIRMADO, null))));
    }

    // executa a Specification que o service mandou pro repositorio e devolve o CriteriaBuilder usado
    @SuppressWarnings("unchecked")
    private CriteriaBuilder filtroAplicadoNaConsulta() {
        ArgumentCaptor<Specification<Transfer>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(spec.capture(), any(Pageable.class));
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
        spec.getValue().toPredicate(mock(Root.class, RETURNS_MOCKS), mock(CriteriaQuery.class), cb);
        return cb;
    }

    // ---------------- 404 ----------------

    @Test
    void buscaDeTransferInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(TransferNaoEncontradoException.class);
    }

    @Test
    void putDeTransferInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        TransferRequestDTO put = patch(DIA, LocalTime.NOON, "A", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, put)).isInstanceOf(TransferNaoEncontradoException.class);
    }

    @Test
    void patchDeTransferInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        TransferRequestDTO soOrigem = patch(null, null, "C", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, soOrigem)).isInstanceOf(TransferNaoEncontradoException.class);
    }

    @Test
    void patchDeMoedaEmTransferInexistenteLanca404AntesDaCotacao() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        TransferRequestDTO soMoeda = patch(null, null, null, "EUR", null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, soMoeda)).isInstanceOf(TransferNaoEncontradoException.class);
        verifyNoInteractions(cotacaoService);
    }

    @Test
    void excluirTransferInexistenteLanca404SemApagar() {
        // Given
        when(repository.existsById(9L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(TransferNaoEncontradoException.class);
        verify(repository, never()).deleteById(anyLong());
    }

    // ---------------- listagem ----------------

    @Test
    @SuppressWarnings("unchecked")
    void periodoInvertidoNoFiltroLanca400SemConsultar() {
        // Given
        FiltroTransferDTO invertido = new FiltroTransferDTO(null, DIA, DIA.minusDays(1), null, null, null);
        PageRequest pagina = PageRequest.of(0, 5);

        // When / Then
        assertThatThrownBy(() -> service.listar(invertido, pagina)).isInstanceOf(RegraNegocioException.class);
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void filtroSemOsLigadoRestringeAosTransfersSemOrdemDeServico() {
        // Given
        repositorioDevolveUmTransfer();

        // When
        PaginaResponseDTO<TransferResponseDTO> pagina = service.listar(filtroSemOs(true), PageRequest.of(0, 5));

        // Then
        assertThat(pagina.conteudo()).hasSize(1);
        verify(filtroAplicadoNaConsulta()).isNull(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void filtroSemOsDesligadoOuAusenteNaoRestringePorOs(Boolean semOs) {
        // Given
        repositorioDevolveUmTransfer();

        // When
        service.listar(filtroSemOs(semOs), PageRequest.of(0, 5));

        // Then
        verify(filtroAplicadoNaConsulta(), never()).isNull(any());
    }

    // ---------------- escrita ----------------

    @Test
    void patchCampoACampoETrocandoSoAMoedaRecalculaComOValorSalvo() {
        // Given
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.AGUARDANDO_OS, null)));
        when(cotacaoService.obterCotacao("EUR", "BRL")).thenReturn(new CotacaoDTO("EUR", "BRL", new BigDecimal("6.5"), null));

        // When
        TransferResponseDTO salvo = service.atualizarParcial(5L, patch(DIA.plusDays(1), LocalTime.MIDNIGHT, "Rodoviaria", "eur", null));

        // Then
        assertThat(salvo.dataTransfer()).isEqualTo(DIA.plusDays(1));
        assertThat(salvo.horaTransfer()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(salvo.origem()).isEqualTo("Rodoviaria");
        assertThat(salvo.moedaOrigem()).isEqualTo("EUR");
        assertThat(salvo.valorBase()).isEqualByComparingTo("65.00"); // 10 (ja salvo) x 6,5
    }

    @Test
    void putComPassageirosTrocaALista() {
        // Given
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.AGUARDANDO_OS, null)));
        when(passageiroRepository.findAllById(Set.of(1L))).thenReturn(List.of(Passageiro.builder().id(1L).nome("Ana").build()));
        TransferRequestDTO put = new TransferRequestDTO(DIA, LocalTime.NOON, "A", "B", null, new BigDecimal("50"), null,
                "BRL", null, Set.of(1L), null);

        // When
        TransferResponseDTO salvo = service.atualizar(5L, put);

        // Then
        assertThat(salvo.passageiroIds()).containsExactly(1L);
    }

    @Test
    void sairDaOsEmAndamentoNaoVoltaPraAguardandoOs() {
        // Given
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.EM_ANDAMENTO, os(3L, StatusOrdemServico.EM_ANDAMENTO))));
        TransferRequestDTO semOs = new TransferRequestDTO(DIA, LocalTime.NOON, "A", "B", StatusTransfer.EM_ANDAMENTO,
                new BigDecimal("1"), null, "BRL", null, null, null);

        // When
        TransferResponseDTO salvo = service.atualizar(5L, semOs);

        // Then
        assertThat(salvo.status()).isEqualTo(StatusTransfer.EM_ANDAMENTO); // so CONFIRMADO volta
        assertThat(salvo.osId()).isNull();
    }

    @Test
    void mudarSoADataDentroDaMesmaOsTambemConfereODia() {
        // Given
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os(3L, StatusOrdemServico.ABERTA))));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os(3L, StatusOrdemServico.ABERTA)));
        TransferRequestDTO outroDia = patch(DIA.plusDays(1), null, null, null, 3L);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(5L, outroDia))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("mesmo dia");
    }

    @Test
    void naoEntraEmOsFinalizada() {
        // Given
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os(3L, StatusOrdemServico.ABERTA))));
        when(ordemServicoRepository.findById(4L)).thenReturn(Optional.of(os(4L, StatusOrdemServico.FINALIZADA)));
        TransferRequestDTO paraOsFinalizada = patch(null, null, null, null, 4L);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(5L, paraOsFinalizada))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("FINALIZADA");
    }

    @Test
    void trocarDeUmaOsAbertaParaOutraEmAndamentoPode() {
        // Given
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os(3L, StatusOrdemServico.ABERTA))));
        when(ordemServicoRepository.findById(6L)).thenReturn(Optional.of(os(6L, StatusOrdemServico.EM_ANDAMENTO)));

        // When
        TransferResponseDTO salvo = service.atualizarParcial(5L, patch(null, null, null, null, 6L));

        // Then
        assertThat(salvo.osId()).isEqualTo(6L);
        assertThat(salvo.status()).isEqualTo(StatusTransfer.CONFIRMADO);
    }

    @Test
    void exclusaoApagaEAudita() {
        // Given
        when(repository.existsById(5L)).thenReturn(true);

        // When
        service.deletar(5L);

        // Then
        verify(repository).deleteById(5L);
        verify(auditoriaService).registrar("transfers", 5L, "Transfer removido");
    }
}
