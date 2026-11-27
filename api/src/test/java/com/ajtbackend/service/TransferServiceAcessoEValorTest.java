package com.ajtbackend.service;

import com.ajtbackend.dto.FiltroTransferDTO;
import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.dto.TransferRequestDTO;
import com.ajtbackend.dto.TransferResponseDTO;
import com.ajtbackend.exception.CotacaoIndisponivelException;
import com.ajtbackend.exception.RegraNegocioException;
import com.ajtbackend.exception.TransferNaoEncontradoException;
import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.ParadaOs;
import com.ajtbackend.model.Transfer;
import com.ajtbackend.model.enums.StatusOrdemServico;
import com.ajtbackend.model.enums.StatusTransfer;
import com.ajtbackend.repository.OrdemServicoRepository;
import com.ajtbackend.repository.ParadaOsRepository;
import com.ajtbackend.repository.PassageiroRepository;
import com.ajtbackend.repository.TransferRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: as regras do TransferService que vieram da revisao de seguranca e de dominio —
 * o MOTORISTA so enxerga transfers das OS dele e sem os valores (HU07, ADR-0019); o PATCH que
 * muda o valor em moeda estrangeira nunca deixa o valor em reais da moeda antiga quando a
 * cotacao falha; transfer sem OS nao fica confirmado/em andamento/concluido; e quem sai de
 * uma OS sai tambem do roteiro de paradas dela.
 *
 * como rodar: teste unitario com repositorios e AcessoMotorista mockados e o TransactionTemplate
 * executando o callback direto. sem spring, sem banco. "mvn test".
 *
 * por que existe: cada um desses casos deixava dado errado sem nenhum erro aparente (faturamento
 * com valor velho, motorista vendo valores e passageiros de outras OS, roteiro com transfer de
 * outra OS).
 */
class TransferServiceAcessoEValorTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 21);
    private static final Pageable PAGINA = PageRequest.of(0, 5);

    private final TransferRepository repository = mock(TransferRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final PassageiroRepository passageiroRepository = mock(PassageiroRepository.class);
    private final ParadaOsRepository paradaOsRepository = mock(ParadaOsRepository.class);
    private final AcessoMotorista acessoMotorista = mock(AcessoMotorista.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final CotacaoService cotacaoService = mock(CotacaoService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private TransferService service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        service = new TransferService(repository, ordemServicoRepository, passageiroRepository, paradaOsRepository,
                new ValorTransferService(cotacaoService), acessoMotorista, auditoriaService, transactionTemplate);
    }

    private static OrdemServico os(Long id, Long motoristaId) {
        return OrdemServico.builder().id(id).dataServico(DIA).status(StatusOrdemServico.ABERTA)
                .motorista(Motorista.builder().id(motoristaId).build()).build();
    }

    private static Transfer transfer(StatusTransfer status, OrdemServico os) {
        return Transfer.builder().id(5L).dataTransfer(DIA).horaTransfer(LocalTime.NOON).origem("A").destino("B")
                .status(status).valorBase(new BigDecimal("500.00")).valorOriginal(new BigDecimal("100.00"))
                .moedaOrigem("USD").ordemServico(os).build();
    }

    private void logadoComoMotorista(Long motoristaId) {
        when(acessoMotorista.restrito()).thenReturn(true);
        when(acessoMotorista.motoristaId()).thenReturn(Optional.ofNullable(motoristaId));
        when(acessoMotorista.podeVer(any())).thenAnswer(inv -> {
            OrdemServico os = inv.getArgument(0);
            return os.getMotorista().getId().equals(motoristaId);
        });
    }

    private static TransferRequestDTO patch(StatusTransfer status, BigDecimal valorOriginal, String moeda, Long osId) {
        return new TransferRequestDTO(null, null, null, null, status, null, valorOriginal, moeda, osId, null, null);
    }

    // ---------------- MOTORISTA (HU07) ----------------

    @Test
    void deveDevolverSemValores_quandoMotoristaBuscaTransferDaOsDele() {
        // given
        logadoComoMotorista(7L);
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os(3L, 7L))));

        // when
        TransferResponseDTO resposta = service.buscarPorId(5L);

        // then
        assertThat(resposta.id()).isEqualTo(5L);
        assertThat(resposta.valorBase()).isNull();
        assertThat(resposta.valorOriginal()).isNull();
        assertThat(resposta.moedaOrigem()).isNull();
    }

    @Test
    void deveLancarNaoEncontrado_quandoMotoristaBuscaTransferDeOsDeOutro() {
        // given
        logadoComoMotorista(7L);
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os(3L, 8L))));

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(5L));

        // then: nem fica sabendo que o transfer existe
        assertThat(erro).isInstanceOf(TransferNaoEncontradoException.class);
    }

    @Test
    void deveLancarNaoEncontrado_quandoMotoristaBuscaTransferSemOs() {
        // given
        logadoComoMotorista(7L);
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.AGUARDANDO_OS, null)));

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(5L));

        // then
        assertThat(erro).isInstanceOf(TransferNaoEncontradoException.class);
    }

    @Test
    void deveDevolverComValores_quandoGerenteBuscaQualquerTransfer() {
        // given
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.AGUARDANDO_OS, null)));

        // when
        TransferResponseDTO resposta = service.buscarPorId(5L);

        // then
        assertThat(resposta.valorBase()).isEqualByComparingTo("500.00");
        assertThat(resposta.moedaOrigem()).isEqualTo("USD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveFiltrarPelasOsDoMotorista_quandoMotoristaListaOuBuscaPorStatus() {
        // given
        logadoComoMotorista(7L);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transfer(StatusTransfer.CONFIRMADO, os(3L, 7L))), PAGINA, 1));

        // when
        PaginaResponseDTO<TransferResponseDTO> lista = service.listar(new FiltroTransferDTO(null, null, null, null, null, null), PAGINA);
        PaginaResponseDTO<TransferResponseDTO> porStatus = service.buscarPorStatus(StatusTransfer.CONFIRMADO, PAGINA);

        // then: as duas leituras passam pela consulta com filtro (nunca o findByStatus sem restricao)
        assertThat(lista.conteudo()).extracting(TransferResponseDTO::valorBase).containsOnlyNulls();
        assertThat(porStatus.totalElementos()).isEqualTo(1);
        verify(acessoMotorista, times(2)).motoristaId();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveConsultarSemResultado_quandoMotoristaNaoTemCadastroLigado() {
        // given
        logadoComoMotorista(null);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

        // when
        PaginaResponseDTO<TransferResponseDTO> lista = service.buscarPorStatus(StatusTransfer.CONFIRMADO, PAGINA);

        // then
        assertThat(lista.conteudo()).isEmpty();
    }

    // ---------------- valor do PATCH (cotacao fora do ar) ----------------

    @Test
    void deveZerarOValorEmReais_quandoPatchMudaValorEstrangeiroECotacaoFalha() {
        // given: 100 USD = 500 BRL gravados; o PATCH muda pra 200 USD com a API de cambio fora do ar
        Transfer existente = transfer(StatusTransfer.AGUARDANDO_OS, null);
        when(repository.findById(5L)).thenReturn(Optional.of(existente));
        when(cotacaoService.obterCotacao("USD", "BRL")).thenThrow(new CotacaoIndisponivelException("USD", "BRL", null));

        // when
        TransferResponseDTO resposta = service.atualizarParcial(5L, patch(null, new BigDecimal("200.00"), null, null));

        // then: nunca 500 BRL (o valor da quantia antiga); fica sem valor ate alguem informar
        assertThat(resposta.valorOriginal()).isEqualByComparingTo("200.00");
        assertThat(resposta.valorBase()).isNull();
    }

    @Test
    void deveManterOValorEmReais_quandoPatchNaoMexeNoValor() {
        // given
        Transfer existente = transfer(StatusTransfer.AGUARDANDO_OS, null);
        when(repository.findById(5L)).thenReturn(Optional.of(existente));

        // when
        TransferResponseDTO resposta = service.atualizarParcial(5L, patch(StatusTransfer.CANCELADO, null, null, null));

        // then
        assertThat(resposta.valorBase()).isEqualByComparingTo("500.00");
    }

    // ---------------- status x OS ----------------

    @Test
    void deveLancarRegraNegocioSemGravar_quandoCriaTransferConcluidoSemOs() {
        // given
        TransferRequestDTO dto = new TransferRequestDTO(DIA, LocalTime.NOON, "A", "B", StatusTransfer.CONCLUIDO,
                new BigDecimal("100.00"), null, "BRL", null, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro).isInstanceOf(RegraNegocioException.class).hasMessageContaining("CONCLUIDO");
        verify(repository, never()).save(any());
    }

    // ---------------- roteiro de paradas ----------------

    @Test
    void deveSairDasParadasDaOsAntiga_quandoTransferSaiDaOs() {
        // given
        OrdemServico os = os(3L, 7L);
        Transfer existente = transfer(StatusTransfer.CONFIRMADO, os);
        ParadaOs parada = ParadaOs.builder().id(1L).ordemServico(os).transfers(new HashSet<>(Set.of(existente))).build();
        when(repository.findById(5L)).thenReturn(Optional.of(existente));
        when(paradaOsRepository.findByOrdemServicoIdAndTransfersId(3L, 5L)).thenReturn(List.of(parada));
        TransferRequestDTO put = new TransferRequestDTO(DIA, LocalTime.NOON, "A", "B", null,
                new BigDecimal("100.00"), null, "BRL", null, null, null);

        // when
        TransferResponseDTO resposta = service.atualizar(5L, put);

        // then
        assertThat(resposta.osId()).isNull();
        assertThat(resposta.status()).isEqualTo(StatusTransfer.AGUARDANDO_OS);
        assertThat(parada.getTransfers()).isEmpty();
    }

    @Test
    void naoDeveMexerNasParadas_quandoTransferContinuaNaMesmaOs() {
        // given
        OrdemServico os = os(3L, 7L);
        when(repository.findById(5L)).thenReturn(Optional.of(transfer(StatusTransfer.CONFIRMADO, os)));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));

        // when
        service.atualizarParcial(5L, patch(null, null, null, 3L));

        // then
        verify(paradaOsRepository, never()).findByOrdemServicoIdAndTransfersId(any(), any());
    }
}
