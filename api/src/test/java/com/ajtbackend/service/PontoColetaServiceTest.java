package com.ajtbackend.service;

import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.dto.PontoColetaRequestDTO;
import com.ajtbackend.dto.PontoColetaResponseDTO;
import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.PontoColetaNaoEncontradoException;
import com.ajtbackend.exception.TransferNaoEncontradoException;
import com.ajtbackend.model.PontoColeta;
import com.ajtbackend.model.Transfer;
import com.ajtbackend.repository.PontoColetaRepository;
import com.ajtbackend.repository.TransferRepository;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: as regras do PontoColetaService: listagens, 404 de ponto e de
 * transfer inexistentes (sem alterar o ponto), versao velha (409), PATCH campo
 * a campo (inclusive trocando o transfer) e exclusao.
 *
 * como rodar: teste unitario com os repositorios mockados. sem spring, sem
 * banco. "mvn test".
 *
 * por que existe: o ponto de coleta e onde o passageiro espera o motorista.
 * um ponto ligado ao transfer errado ou editado em cima de dado velho manda
 * o motorista ao endereco errado sem nenhum erro aparente.
 */
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
    void deveDevolverAPaginaDoRepositorio_quandoListaTodos() {
        // given
        Pageable pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(ponto()), pagina, 1));

        // when
        PaginaResponseDTO<PontoColetaResponseDTO> resultado = service.listarTodos(pagina);

        // then
        assertThat(resultado.conteudo()).extracting(PontoColetaResponseDTO::localColeta).containsExactly("Portao 2");
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void deveDevolverOsPontosDaViagem_quandoListaPorTransfer() {
        // given
        when(repository.findByTransferIdOrderByOrdemParadaAsc(7L)).thenReturn(List.of(ponto()));

        // when
        List<PontoColetaResponseDTO> resultado = service.listarPorTransfer(7L);

        // then
        assertThat(resultado).extracting(PontoColetaResponseDTO::transferId).containsExactly(7L);
        assertThat(resultado).extracting(PontoColetaResponseDTO::ordemParada).containsExactly(1);
    }

    @Test
    void deveDevolverOPonto_quandoBuscaIdExistente() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        // when
        PontoColetaResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.id()).isEqualTo(1L);
        assertThat(resposta.localColeta()).isEqualTo("Portao 2");
        assertThat(resposta.transferId()).isEqualTo(7L);
    }

    // ----- ponto inexistente (404) -----

    @Test
    void deveLancarPontoNaoEncontrado_quandoBuscaIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro)
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
    }

    @Test
    void deveLancarPontoNaoEncontradoSemGravar_quandoPutEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO dto = dto(7L, "X", null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void deveLancarPontoNaoEncontradoSemGravar_quandoPatchEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(9L, patchVazio()));

        // then
        assertThat(erro)
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).flush();
    }

    @Test
    void deveLancarPontoNaoEncontradoSemApagar_quandoExcluiIdInexistente() {
        // given
        when(repository.existsById(9L)).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro)
                .isInstanceOf(PontoColetaNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(repository, never()).deleteById(any());
    }

    // ----- transfer inexistente (404) -----

    @Test
    void deveLancarTransferNaoEncontradoSemGravar_quandoCadastroApontaTransferInexistente() {
        // given
        when(transferRepository.findById(42L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO dto = dto(42L, "P", null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("42");
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarTransferNaoEncontradoSemAlterar_quandoPutApontaTransferInexistente() {
        // given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(transferRepository.findById(42L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO dto = dto(42L, "P", null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("42");
        assertThat(existente.getLocalColeta()).isEqualTo("Portao 2");
        assertThat(existente.getTransfer().getId()).isEqualTo(7L);
        verify(repository, never()).flush();
    }

    @Test
    void deveLancarTransferNaoEncontradoSemAlterar_quandoPatchApontaTransferInexistente() {
        // given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(transferRepository.findById(42L)).thenReturn(Optional.empty());
        PontoColetaRequestDTO patch = dto(42L, null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("42");
        assertThat(existente.getTransfer().getId()).isEqualTo(7L);
        verify(repository, never()).flush();
    }

    // ----- cadastro e edicao validos -----

    @Test
    void deveGravarOPontoLigadoAoTransfer_quandoCadastroApontaTransferExistente() {
        // given
        Transfer transfer = Transfer.builder().id(7L).build();
        when(transferRepository.findById(7L)).thenReturn(Optional.of(transfer));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        PontoColetaResponseDTO criado = service.criar(dto(7L, "Saguao", null));

        // then
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
    void deveTrocarOsCamposEGravarNaHora_quandoPutTrazVersaoCerta() {
        // given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(transferRepository.findById(7L)).thenReturn(Optional.of(Transfer.builder().id(7L).build()));

        // when
        PontoColetaResponseDTO atualizado = service.atualizar(1L, dto(7L, "Saguao B", 0L));

        // then
        assertThat(atualizado.localColeta()).isEqualTo("Saguao B");
        assertThat(atualizado.ordemParada()).isEqualTo(2);
        assertThat(atualizado.horarioPrevisto()).isEqualTo(LocalTime.NOON);
        assertThat(existente.getLocalColeta()).isEqualTo("Saguao B");
        verify(repository).flush();
    }

    // ----- versao desatualizada (409) -----

    @Test
    void deveLancarConflitoSemAlterar_quandoPutTrazVersaoVelha() {
        // given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        PontoColetaRequestDTO dto = dto(7L, "P", 5L);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(existente.getLocalColeta()).isEqualTo("Portao 2");
        verify(repository, never()).flush();
    }

    @Test
    void deveLancarConflitoSemAlterar_quandoPatchTrazVersaoVelha() {
        // given
        PontoColeta existente = ponto();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        PontoColetaRequestDTO patch = new PontoColetaRequestDTO(null, "P", null, null, null, null, 5L);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(ConflitoDeVersaoException.class)
                .hasMessageContaining("alterado por outro usuário");
        assertThat(existente.getLocalColeta()).isEqualTo("Portao 2");
        verify(repository, never()).flush();
    }

    // ----- PATCH -----

    @Test
    void deveMudarTudoInclusiveOTransfer_quandoPatchTrazTodosOsCampos() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));
        when(transferRepository.findById(8L)).thenReturn(Optional.of(Transfer.builder().id(8L).build()));
        PontoColetaRequestDTO patch = new PontoColetaRequestDTO(8L, "Novo", 3, LocalTime.MIDNIGHT,
                new BigDecimal("1"), new BigDecimal("2"), null);

        // when
        PontoColetaResponseDTO resposta = service.atualizarParcial(1L, patch);

        // then
        assertThat(resposta.transferId()).isEqualTo(8L);
        assertThat(resposta.localColeta()).isEqualTo("Novo");
        assertThat(resposta.ordemParada()).isEqualTo(3);
        assertThat(resposta.horarioPrevisto()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(resposta.latitude()).isEqualByComparingTo("1");
        assertThat(resposta.longitude()).isEqualByComparingTo("2");
        verify(repository).flush();
    }

    @Test
    void deveManterOPontoIntacto_quandoPatchVemVazio() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(ponto()));

        // when
        PontoColetaResponseDTO resposta = service.atualizarParcial(1L, patchVazio());

        // then
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
    void deveApagarOPonto_quandoExcluiIdExistente() {
        // given
        when(repository.existsById(1L)).thenReturn(true);

        // when
        service.deletar(1L);

        // then
        verify(repository).deleteById(1L);
    }
}
