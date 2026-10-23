package com.AJTBackend.service;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: a orquestracao do TransferService — valida a ordem de servico
 * referenciada, recalcula o valorBase certo no PATCH parcial (a regra de
 * conversao em si esta no ValorTransferServiceTest), vincula passageiros ao transfer (uma unica
 * consulta, id inexistente vira 404) e registra a auditoria de cada escrita.
 *
 * como rodar: teste unitario com mocks (CotacaoService, AuditoriaService e os
 * repositorios mockados; TransactionTemplate mockado executa o callback direto, sem
 * transacao real). sem spring context, sem banco, sem docker. roda com "mvn test".
 *
 * por que existe: e o fluxo principal da operacao (criar e mexer em transfer); as bordas
 * da regra da OS e o 404 de cada operacao ficam no TransferServiceRegrasTest.
 */
class TransferServiceTest {

    private static final LocalDate DIA_DA_OS = LocalDate.of(2026, 9, 20);

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final PassageiroRepository passageiroRepository = mock(PassageiroRepository.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
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
        service = new TransferService(transferRepository, ordemServicoRepository, passageiroRepository,
                new ValorTransferService(cotacaoService), auditoriaService, transactionTemplate);
    }

    @Test
    void deveLancarOsNaoEncontradaSemGravarNemAuditar_quandoCadastroApontaOsInexistente() {
        // given
        when(ordemServicoRepository.findById(99L)).thenReturn(Optional.empty());
        TransferRequestDTO dto = dto(null, null, null, 99L);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(OrdemServicoNaoEncontradoException.class)
                .hasMessageContaining("99");
        verify(transferRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any());
    }

    @Test
    void deveRecalcularComAMoedaJaSalva_quandoPatchMudaSoOValorOriginal() {
        // given
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").valorOriginal(new BigDecimal("10.00")).moedaOrigem("USD")
                .valorBase(new BigDecimal("50.00")).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(cotacaoService.obterCotacao("USD", "BRL"))
                .thenReturn(new CotacaoDTO("USD", "BRL", new BigDecimal("6"), null));
        TransferRequestDTO patch = new TransferRequestDTO(null, null, null, null, null,
                null, new BigDecimal("20.00"), null, null, null, null);

        // when
        TransferResponseDTO atualizado = service.atualizarParcial(5L, patch);

        // then
        assertThat(atualizado.valorOriginal()).isEqualByComparingTo("20.00");
        assertThat(atualizado.valorBase()).isEqualByComparingTo("120.00");
        assertThat(atualizado.moedaOrigem()).isEqualTo("USD"); // moeda ja salva e mantida
        assertThat(atualizado.origem()).isEqualTo("A"); // campos nao enviados ficam intactos
        assertThat(atualizado.destino()).isEqualTo("B");
        verify(cotacaoService).obterCotacao("USD", "BRL");
        verify(transferRepository).flush();
    }

    @Test
    void naoDeveConsultarCotacao_quandoPatchMudaSoOStatus() {
        // given
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        TransferRequestDTO patch = patchDeStatus(StatusTransfer.CONFIRMADO, null);

        // when
        TransferResponseDTO resposta = service.atualizarParcial(5L, patch);

        // then
        assertThat(resposta.status()).isEqualTo(StatusTransfer.CONFIRMADO);
        assertThat(existente.getStatus()).isEqualTo(StatusTransfer.CONFIRMADO);
        verify(cotacaoService, never()).obterCotacao(anyString(), anyString());
        verify(transferRepository).flush();
    }

    @Test
    void deveLancarConflitoSemAlterarNada_quandoClienteMandaVersaoVelha() {
        // given
        Transfer existente = Transfer.builder().id(5L).versao(2L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").status(StatusTransfer.AGUARDANDO_OS).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        TransferRequestDTO patch = patchDeStatus(StatusTransfer.CONFIRMADO, 1L);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(5L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(existente.getStatus()).isEqualTo(StatusTransfer.AGUARDANDO_OS);
        verify(transferRepository, never()).save(any());
        verify(auditoriaService, never()).registrarAtualizacao(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deveLancarTransferNaoEncontradoSemGravar_quandoPutEmIdInexistente() {
        // given
        when(transferRepository.findById(1L)).thenReturn(Optional.empty());
        TransferRequestDTO dto = dto(null, null, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("1");
        verify(transferRepository, never()).save(any());
    }

    @Test
    void deveVincularOsPassageirosNumaConsultaSo_quandoCadastroTrazPassageiros() {
        // given
        when(passageiroRepository.findAllById(any())).thenReturn(List.of(passageiro(1L), passageiro(2L)));
        TransferRequestDTO dto = new TransferRequestDTO(DIA_DA_OS, LocalTime.of(14, 30),
                "GRU", "Hotel", null, null, null, null, null, Set.of(1L, 2L), null);

        // when
        TransferResponseDTO criado = service.criar(dto);

        // then
        assertThat(criado.passageiroIds()).containsExactlyInAnyOrder(1L, 2L);
        verify(passageiroRepository).findAllById(any());
        Transfer gravado = capturarTransferGravado();
        assertThat(gravado.getPassageiros()).extracting(Passageiro::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void deveLancarPassageiroNaoEncontradoSemGravar_quandoUmPassageiroNaoExiste() {
        // given
        when(passageiroRepository.findAllById(any())).thenReturn(List.of(passageiro(1L)));
        // ordem fixa (o que existe primeiro): com Set.of a ordem muda a cada execucao e o filtro
        // que procura o faltante as vezes nem passa pelo id que existe
        Set<Long> ids = new LinkedHashSet<>(List.of(1L, 99L));
        TransferRequestDTO dto = new TransferRequestDTO(DIA_DA_OS, LocalTime.of(14, 30),
                "GRU", "Hotel", null, null, null, null, null, ids, null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("99");
        verify(transferRepository, never()).save(any());
    }

    @Test
    void deveManterOsPassageirosAtuais_quandoPutNaoTrazPassageiroIds() {
        // given
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").passageiros(new HashSet<>(Set.of(passageiro(7L)))).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));

        // when
        TransferResponseDTO atualizado = service.atualizar(5L, dto(null, null, null, null));

        // then
        assertThat(atualizado.passageiroIds()).containsExactly(7L);
        verify(passageiroRepository, never()).findAllById(any());
        verify(transferRepository).flush();
    }

    @Test
    void deveRemoverTodosOsPassageiros_quandoPatchTrazListaVazia() {
        // given
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").passageiros(new HashSet<>(Set.of(passageiro(7L)))).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        TransferRequestDTO patch = new TransferRequestDTO(null, null, null, null, null,
                null, null, null, null, Set.of(), null);

        // when
        TransferResponseDTO atualizado = service.atualizarParcial(5L, patch);

        // then
        assertThat(atualizado.passageiroIds()).isEmpty();
        assertThat(existente.getPassageiros()).isEmpty();
        verify(transferRepository).flush();
    }

    @Test
    void deveVincularEConfirmar_quandoCadastroApontaOsExistenteDoMesmoDia() {
        // given
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(DIA_DA_OS).build();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));

        // when
        TransferResponseDTO criado = service.criar(dto(null, null, null, 3L));

        // then
        assertThat(criado.osId()).isEqualTo(3L);
        assertThat(criado.status()).isEqualTo(StatusTransfer.CONFIRMADO); // com OS nao fica "aguardando OS"
        Transfer gravado = capturarTransferGravado(); // o que foi gravado, nao so o que voltou
        assertThat(gravado.getOrdemServico()).isSameAs(os);
        assertThat(gravado.getStatus()).isEqualTo(StatusTransfer.CONFIRMADO);
    }

    @Test
    void deveLancarRegraNegocioSemGravar_quandoOsEhDeOutroDia() {
        // given
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(LocalDate.of(2026, 9, 21)).build();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));
        TransferRequestDTO dto = dto(null, null, null, 3L);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("20/09/2026")
                .hasMessageContaining("21/09/2026");
        verify(transferRepository, never()).save(any());
    }

    @Test
    void deveLancarRegraNegocioSemGravar_quandoOsEstaCancelada() {
        // given
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(DIA_DA_OS)
                .status(StatusOrdemServico.CANCELADA).build();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));
        TransferRequestDTO dto = dto(null, null, null, 3L);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CANCELADA");
        verify(transferRepository, never()).save(any());
    }

    @Test
    void deveVoltarParaAguardandoOs_quandoPutSaiDaOs() {
        // given
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(DIA_DA_OS).build();
        Transfer existente = Transfer.builder().id(5L).dataTransfer(DIA_DA_OS).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").status(StatusTransfer.CONFIRMADO).ordemServico(os).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        // PUT sem osId = desvincular (e o que a tela da OS faz)
        TransferRequestDTO put = new TransferRequestDTO(DIA_DA_OS, LocalTime.NOON, "A", "B",
                StatusTransfer.CONFIRMADO, null, null, null, null, null, null);

        // when
        TransferResponseDTO resposta = service.atualizar(5L, put);

        // then
        assertThat(resposta.status()).isEqualTo(StatusTransfer.AGUARDANDO_OS);
        assertThat(resposta.osId()).isNull();
        verify(transferRepository).flush();
    }

    @Test
    void devePermitirEditar_quandoTransferDeOsFinalizadaNaoMudaOsNemData() {
        // given
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(DIA_DA_OS)
                .status(StatusOrdemServico.FINALIZADA).build();
        Transfer existente = Transfer.builder().id(5L).dataTransfer(DIA_DA_OS).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").status(StatusTransfer.CONCLUIDO).ordemServico(os).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        TransferRequestDTO patch = new TransferRequestDTO(null, null, null, "Hotel novo", null,
                null, null, null, null, null, null);

        // when
        TransferResponseDTO resposta = service.atualizarParcial(5L, patch);

        // then
        assertThat(resposta.destino()).isEqualTo("Hotel novo");
        assertThat(resposta.status()).isEqualTo(StatusTransfer.CONCLUIDO); // status nao muda
        assertThat(resposta.osId()).isEqualTo(3L); // continua na mesma OS
    }

    @Test
    void deveRegistrarAuditoria_quandoCriaTransfer() {
        // when
        service.criar(dto(null, null, null, null));

        // then
        verify(auditoriaService).registrar(eq("transfers"), eq(10L), contains("Transfer criado"));
    }

    @Test
    void deveRegistrarAuditoriaComDeEPara_quandoStatusMuda() {
        // given
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").status(StatusTransfer.AGUARDANDO_OS).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        TransferRequestDTO patch = patchDeStatus(StatusTransfer.CONFIRMADO, null);

        // when
        service.atualizarParcial(5L, patch);

        // then
        verify(auditoriaService).registrarAtualizacao(eq("transfers"), eq(5L), eq("Transfer atualizado"),
                eq(StatusTransfer.AGUARDANDO_OS), eq(StatusTransfer.CONFIRMADO), isNull(), isNull());
    }

    private Transfer capturarTransferGravado() {
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(captor.capture());
        return captor.getValue();
    }

    private static TransferRequestDTO dto(BigDecimal valorBase, BigDecimal valorOriginal, String moeda, Long osId) {
        return new TransferRequestDTO(DIA_DA_OS, LocalTime.of(14, 30), "Aeroporto GRU", "Hotel",
                null, valorBase, valorOriginal, moeda, osId, null, null);
    }

    private static TransferRequestDTO patchDeStatus(StatusTransfer status, Long versao) {
        return new TransferRequestDTO(null, null, null, null, status,
                null, null, null, null, null, versao);
    }

    private static Passageiro passageiro(Long id) {
        return Passageiro.builder().id(id).nome("Passageiro " + id).tipoDocumento("PASSAPORTE")
                .documento("X" + id).build();
    }
}
