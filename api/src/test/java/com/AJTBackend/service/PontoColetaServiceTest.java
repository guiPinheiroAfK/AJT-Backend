package com.AJTBackend.service;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.PontoColetaRequestDTO;
import com.AJTBackend.dto.PontoColetaResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.PontoColetaNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.PontoColeta;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.repository.PontoColetaRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

class PontoColetaServiceTest {

    private static final BigDecimal LAT = new BigDecimal("-25.5");
    private static final BigDecimal LON = new BigDecimal("-54.5");

    private final PontoColetaRepository repository = mock(PontoColetaRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final PontoColetaService service = new PontoColetaService(repository, transferRepository);

    private static PontoColeta ponto() {
        return PontoColeta.builder().id(1L).versao(0L).transfer(Transfer.builder().id(7L).build())
                .localColeta("Portao 2").ordemParada(1).latitude(LAT).longitude(LON).build();
    }

    private static PontoColetaRequestDTO dto(Long transferId, String local, Long versao) {
        return new PontoColetaRequestDTO(transferId, local, 2, LocalTime.NOON, LAT, LON, versao);
    }

    private static PontoColetaRequestDTO patchVazio() {
        return new PontoColetaRequestDTO(null, null, null, null, null, null, null);
    }

    // ----- leitura -----

    @Test
    void listarTodosDevolveAPaginaDoRepositorio() {
        // Given
        Pageable pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(ponto()), pagina, 1));

        // When
        PaginaResponseDTO<PontoColetaResponseDTO> resultado = service.listarTodos(pagina);

        // Then
        assertThat(resultado.conteudo()).extracting(PontoColetaResponseDTO::localColeta).containsExactly("Portao 2");
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void listarPorTransferDevolveOsPontosDaquelaViagem() {
        // Given
        when(repository.findByTransferIdOrderByOrdemParadaAsc(7L)).thenReturn(List.of(ponto()));

        // When
        List<PontoColetaResponseDTO> resultado = service.listarPorTransfer(7L);

        // Then
        assertThat(resultado).extracting(PontoColetaResponseDTO::transferId).containsExactly(7L);
        assertThat(resultado).extracting(PontoColetaResponseDTO::ordemParada).containsExactly(1);
    }

    @Test
    void buscarPorIdDevolveOPontoEncontrado() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        // When
        PontoColetaResponseDTO resposta = service.buscarPorId(1L);

        // Then
        assertThat(resposta.id()).isEqualTo(1L);
        assertThat(resposta.localColeta()).isEqualTo("Portao 2");
        assertThat(resposta.transferId()).isEqualTo(7L);
    }

    // ----- ponto inexistente (404) -----

    @Test
    void buscarPorIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L))
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
    }

    @Test
    void atualizarInexistenteLanca404SemGravar() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO dto = dto(7L, "X", null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto))
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void atualizarParcialInexistenteLanca404SemGravar() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, patchVazio()))
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void deletarInexistenteLanca404SemApagarNada() {
        // Given
        when(repository.existsById(9L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L))
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).deleteById(any());
    }

    // ----- transfer inexistente (404) -----

    @Test
    void criarComTransferInexistenteLanca404SemGravar() {
        // Given
        when(transferRepository.findById(42L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO dto = dto(42L, "P", null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("42");
        verify(repository, never()).save(any());
    }

    @Test
    void atualizarComTransferInexistenteLanca404SemAlterarOPonto() {
        // Given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(transferRepository.findById(42L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO dto = dto(42L, "P", null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto))
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("42");
        assertThat(existente.getLocalColeta()).isEqualTo("Portao 2");
        assertThat(existente.getTransfer().getId()).isEqualTo(7L);
        verify(repository, never()).flush();
    }

    @Test
    void atualizarParcialComTransferInexistenteLanca404SemAlterarOPonto() {
        // Given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(transferRepository.findById(42L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO patch = dto(42L, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, patch))
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("42");
        assertThat(existente.getTransfer().getId()).isEqualTo(7L);
        verify(repository, never()).flush();
    }

    // ----- cadastro e edicao validos -----

    @Test
    void criarComTransferExistenteGravaOPontoVinculadoAoTransfer() {
        // Given
        Transfer transfer = Transfer.builder().id(7L).build();
        when(transferRepository.findById(7L)).thenReturn(Optional.of(transfer));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        PontoColetaResponseDTO criado = service.criar(dto(7L, "Saguao", null));

        // Then
        assertThat(criado.localColeta()).isEqualTo("Saguao");
        assertThat(criado.transferId()).isEqualTo(7L);
        ArgumentCaptor<PontoColeta> captor = ArgumentCaptor.forClass(PontoColeta.class);
        verify(repository).save(captor.capture());
        PontoColeta gravado = captor.getValue();
        assertThat(gravado.getTransfer()).isSameAs(transfer);
        assertThat(gravado.getLocalColeta()).isEqualTo("Saguao");
        assertThat(gravado.getOrdemParada()).isEqualTo(2);
        assertThat(gravado.getHorarioPrevisto()).isEqualTo(LocalTime.NOON);
        assertThat(gravado.getLatitude()).isEqualByComparingTo(LAT);
        assertThat(gravado.getLongitude()).isEqualByComparingTo(LON);
    }

    @Test
    void atualizarComVersaoCertaTrocaOsCamposEGravaAgora() {
        // Given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(transferRepository.findById(7L)).thenReturn(Optional.of(Transfer.builder().id(7L).build()));

        // When
        PontoColetaResponseDTO atualizado = service.atualizar(1L, dto(7L, "Saguao B", 0L));

        // Then
        assertThat(atualizado.localColeta()).isEqualTo("Saguao B");
        assertThat(atualizado.ordemParada()).isEqualTo(2);
        assertThat(atualizado.horarioPrevisto()).isEqualTo(LocalTime.NOON);
        assertThat(existente.getLocalColeta()).isEqualTo("Saguao B");
        verify(repository).flush();
    }

    // ----- versao desatualizada (409) -----

    @Test
    void atualizarComVersaoVelhaLanca409SemAlterarOPonto() {
        // Given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        PontoColetaRequestDTO dto = dto(7L, "P", 5L);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto))
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(existente.getLocalColeta()).isEqualTo("Portao 2");
        verify(repository, never()).flush();
    }

    @Test
    void atualizarParcialComVersaoVelhaLanca409SemAlterarOPonto() {
        // Given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        PontoColetaRequestDTO patch = new PontoColetaRequestDTO(null, "P", null, null, null, null, 5L);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, patch))
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(existente.getLocalColeta()).isEqualTo("Portao 2");
        verify(repository, never()).flush();
    }

    // ----- PATCH -----

    @Test
    void patchComTodosOsCamposMudaTudoInclusiveOTransfer() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));
        when(transferRepository.findById(8L)).thenReturn(Optional.of(Transfer.builder().id(8L).build()));
        PontoColetaRequestDTO patch = new PontoColetaRequestDTO(8L, "Novo", 3, LocalTime.MIDNIGHT,
                new BigDecimal("1"), new BigDecimal("2"), null);

        // When
        PontoColetaResponseDTO resposta = service.atualizarParcial(1L, patch);

        // Then
        assertThat(resposta.transferId()).isEqualTo(8L);
        assertThat(resposta.localColeta()).isEqualTo("Novo");
        assertThat(resposta.ordemParada()).isEqualTo(3);
        assertThat(resposta.horarioPrevisto()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(resposta.latitude()).isEqualByComparingTo("1");
        assertThat(resposta.longitude()).isEqualByComparingTo("2");
        verify(repository).flush();
    }

    @Test
    void patchSemCamposMantemOPontoIntacto() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        // When
        PontoColetaResponseDTO resposta = service.atualizarParcial(1L, patchVazio());

        // Then
        assertThat(resposta.transferId()).isEqualTo(7L);
        assertThat(resposta.localColeta()).isEqualTo("Portao 2");
        assertThat(resposta.ordemParada()).isEqualTo(1);
        assertThat(resposta.horarioPrevisto()).isNull();
        assertThat(resposta.latitude()).isEqualByComparingTo(LAT);
        assertThat(resposta.longitude()).isEqualByComparingTo(LON);
        verify(transferRepository, never()).findById(any());
    }

    // ----- exclusao -----

    @Test
    void excluirExistenteApagaOPonto() {
        // Given
        when(repository.existsById(1L)).thenReturn(true);

        // When
        service.deletar(1L);

        // Then
        verify(repository).deleteById(1L);
    }
}