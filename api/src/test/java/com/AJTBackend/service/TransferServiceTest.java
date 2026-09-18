package com.AJTBackend.service;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.PassageiroRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
 * (a conversao de moeda mora no ValorTransferService e tem teste proprio.)
 *
 * como rodar: teste unitario com mocks (CotacaoService, AuditoriaService e os
 * repositorios mockados; TransactionTemplate mockado executa o callback direto, sem
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
    void ordemServicoInexistenteRetorna404() {
        when(ordemServicoRepository.findById(99L)).thenReturn(Optional.empty());

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
                null, new BigDecimal("20.00"), null, null, null);
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
                null, null, null, null, null);
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
                null, valorBase, valorOriginal, moeda, osId, null);
    }

    private static Passageiro passageiro(Long id) {
        return Passageiro.builder().id(id).nome("Passageiro " + id).tipoDocumento("PASSAPORTE")
                .documento("X" + id).build();
    }

    @Test
    void vinculaPassageirosAoCriarComUmaUnicaConsulta() {
        when(passageiroRepository.findAllById(any())).thenReturn(List.of(passageiro(1L), passageiro(2L)));

        TransferRequestDTO dto = new TransferRequestDTO(LocalDate.of(2026, 9, 20), LocalTime.of(14, 30),
                "GRU", "Hotel", null, null, null, null, null, Set.of(1L, 2L));
        TransferResponseDTO criado = service.criar(dto);

        assertThat(criado.passageiroIds()).containsExactlyInAnyOrder(1L, 2L);
        verify(passageiroRepository).findAllById(any());
    }

    @Test
    void passageiroInexistenteRetorna404SemGravar() {
        when(passageiroRepository.findAllById(any())).thenReturn(List.of(passageiro(1L)));

        TransferRequestDTO dto = new TransferRequestDTO(LocalDate.of(2026, 9, 20), LocalTime.of(14, 30),
                "GRU", "Hotel", null, null, null, null, null, Set.of(1L, 99L));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(PassageiroNaoEncontradoException.class)
                .hasMessageContaining("99");
        verify(transferRepository, never()).save(any());
    }

    @Test
    void putSemPassageiroIdsMantemOsPassageirosAtuais() {
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").passageiros(new HashSet<>(Set.of(passageiro(7L)))).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));

        TransferResponseDTO atualizado = service.atualizar(5L, dto(null, null, null, null));

        assertThat(atualizado.passageiroIds()).containsExactly(7L);
        verify(passageiroRepository, never()).findAllById(any());
    }

    @Test
    void enviarListaVaziaRemoveTodosOsPassageiros() {
        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").passageiros(new HashSet<>(Set.of(passageiro(7L)))).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));

        TransferRequestDTO patch = new TransferRequestDTO(null, null, null, null, null,
                null, null, null, null, Set.of());

        assertThat(service.atualizarParcial(5L, patch).passageiroIds()).isEmpty();
    }

    @Test
    void vinculaOrdemDeServicoExistente() {
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(LocalDate.now()).build();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));

        assertThat(service.criar(dto(null, null, null, 3L)).osId()).isEqualTo(3L);
    }

    @Test
    void registraAuditoriaNaCriacaoEnaMudancaDeStatus() {
        service.criar(dto(null, null, null, null));
        verify(auditoriaService).registrar(eq("transfers"), eq(10L), contains("Transfer criado"));

        Transfer existente = Transfer.builder().id(5L).dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                .origem("A").destino("B").status(StatusTransfer.AGUARDANDO_OS).build();
        when(transferRepository.findById(5L)).thenReturn(Optional.of(existente));
        service.atualizarParcial(5L, new TransferRequestDTO(null, null, null, null, StatusTransfer.CONFIRMADO,
                null, null, null, null, null));

        verify(auditoriaService).registrarAtualizacao(eq("transfers"), eq(5L), eq("Transfer atualizado"),
                eq(StatusTransfer.AGUARDANDO_OS), eq(StatusTransfer.CONFIRMADO));
    }
}
