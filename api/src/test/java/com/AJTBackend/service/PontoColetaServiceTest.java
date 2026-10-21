package com.AJTBackend.service;

import com.AJTBackend.dto.PontoColetaRequestDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.PontoColetaNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.PontoColeta;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.repository.PontoColetaRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: as regras do PontoColetaService — o ponto precisa de um transfer que exista
 * (no cadastro, no PUT e no PATCH que troca o transfer), id inexistente (404), versao
 * velha (409), PATCH so mexendo no que veio, e a listagem por transfer.
 *
 * como rodar: teste unitario com os repositorios mockados. sem spring, sem banco. "mvn test".
 *
 * por que existe: ponto de coleta solto (sem transfer) some do roteiro do motorista.
 */
class PontoColetaServiceTest {

    private final PontoColetaRepository repository = mock(PontoColetaRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final PontoColetaService service = new PontoColetaService(repository, transferRepository);

    private static final BigDecimal LAT = new BigDecimal("-25.5");
    private static final BigDecimal LON = new BigDecimal("-54.5");

    private static PontoColeta ponto() {
        return PontoColeta.builder().id(1L).versao(0L).transfer(Transfer.builder().id(7L).build())
                .localColeta("Portao 2").ordemParada(1).latitude(LAT).longitude(LON).build();
    }

    private static PontoColetaRequestDTO dto(Long transferId, String local, Long versao) {
        return new PontoColetaRequestDTO(transferId, local, 2, LocalTime.NOON, LAT, LON, versao);
    }

    @Test
    void listagensEBusca() {
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(ponto()), pagina, 1));
        when(repository.findByTransferIdOrderByOrdemParadaAsc(7L)).thenReturn(List.of(ponto()));
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        assertThat(service.listarTodos(pagina).conteudo()).hasSize(1);
        assertThat(service.listarPorTransfer(7L)).extracting(p -> p.transferId()).containsExactly(7L);
        assertThat(service.buscarPorId(1L).localColeta()).isEqualTo("Portao 2");
    }

    @Test
    void idInexistenteLanca404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(PontoColetaNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizar(9L, dto(7L, "X", null))).isInstanceOf(PontoColetaNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto(null, null, null))).isInstanceOf(PontoColetaNaoEncontradoException.class);
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(PontoColetaNaoEncontradoException.class);
    }

    @Test
    void transferInexistenteLanca404SemGravar() {
        when(transferRepository.findById(42L)).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        assertThatThrownBy(() -> service.criar(dto(42L, "P", null))).isInstanceOf(TransferNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizar(1L, dto(42L, "P", null))).isInstanceOf(TransferNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto(42L, null, null))).isInstanceOf(TransferNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroEEdicaoValidos() {
        when(transferRepository.findById(7L)).thenReturn(Optional.of(Transfer.builder().id(7L).build()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        assertThat(service.criar(dto(7L, "Saguao", null)).localColeta()).isEqualTo("Saguao");
        assertThat(service.atualizar(1L, dto(7L, "Saguao B", 0L)).localColeta()).isEqualTo("Saguao B");
    }

    @Test
    void versaoVelhaLanca409() {
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        assertThatThrownBy(() -> service.atualizar(1L, dto(7L, "P", 5L))).isInstanceOf(ConflitoDeVersaoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto(null, null, 5L))).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void patchMudaSoOQueVeio() {
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));
        when(transferRepository.findById(8L)).thenReturn(Optional.of(Transfer.builder().id(8L).build()));

        var tudo = service.atualizarParcial(1L, new PontoColetaRequestDTO(8L, "Novo", 3, LocalTime.MIDNIGHT,
                new BigDecimal("1"), new BigDecimal("2"), null));
        assertThat(tudo.transferId()).isEqualTo(8L);
        assertThat(tudo.localColeta()).isEqualTo("Novo");
        assertThat(tudo.ordemParada()).isEqualTo(3);
        assertThat(tudo.horarioPrevisto()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(tudo.latitude()).isEqualByComparingTo("1");
        assertThat(tudo.longitude()).isEqualByComparingTo("2");

        var nada = service.atualizarParcial(1L, new PontoColetaRequestDTO(null, null, null, null, null, null, null));
        assertThat(nada.localColeta()).isEqualTo("Novo");
    }

    @Test
    void excluirExistenteApaga() {
        when(repository.existsById(1L)).thenReturn(true);

        service.deletar(1L);

        verify(repository).deleteById(1L);
    }
}
