package com.ajtbackend.service;

import com.ajtbackend.dto.FiltroMotoristaDTO;
import com.ajtbackend.dto.MotoristaRequestDTO;
import com.ajtbackend.dto.MotoristaResponseDTO;
import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.exception.CnhJaCadastradaException;
import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.MotoristaNaoEncontradoException;
import com.ajtbackend.model.Motorista;
import com.ajtbackend.repository.MotoristaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
 * o que testa: as regras do MotoristaService — CNH unica no cadastro e na edicao (PUT e
 * PATCH, inclusive quando a CNH nao muda), id e CNH inexistentes (404), versao velha (409),
 * PATCH so mexendo no que veio, e a exclusao de quem nao existe.
 *
 * como rodar: teste unitario com o repositorio mockado. sem spring, sem banco. "mvn test".
 *
 * por que existe: CNH duplicada ou edicao em cima de dado velho estragam a escala dos
 * motoristas sem nenhum erro aparente; os testes de web so provam o status, estes a regra.
 */
class MotoristaServiceTest {

    private final MotoristaRepository repository = mock(MotoristaRepository.class);
    private final MotoristaService service = new MotoristaService(repository);

    private static Motorista carlos() {
        return Motorista.builder().id(1L).versao(3L).nome("Carlos").cnh("111").telefone("45").build();
    }

    private static MotoristaRequestDTO dto(String nome, String cnh, String telefone, Long versao) {
        return new MotoristaRequestDTO(nome, cnh, telefone, null, null, versao);
    }

    private void carlosCadastrado() {
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
    }

    // ----- listagem e busca -----

    @Test
    void deveDevolverAPagina_quandoListaComFiltro() {
        // given
        Pageable pagina = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(carlos()), pagina, 1));

        // when
        PaginaResponseDTO<MotoristaResponseDTO> resultado = service.listar(new FiltroMotoristaDTO("car", null), pagina);

        // then
        assertThat(resultado.conteudo()).extracting(MotoristaResponseDTO::nome).containsExactly("Carlos");
    }

    @Test
    void deveLancarMotoristaNaoEncontrado_quandoIdNaoExiste() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro).isInstanceOf(MotoristaNaoEncontradoException.class).hasMessageContaining("9");
    }

    @Test
    void deveLancarMotoristaNaoEncontrado_quandoCnhNaoExiste() {
        // given
        when(repository.findByCnh("000")).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorCnh("000"));

        // then
        assertThat(erro).isInstanceOf(MotoristaNaoEncontradoException.class).hasMessageContaining("000");
    }

    @Test
    void deveDevolverComAVersao_quandoBuscaIdExistente() {
        // given
        carlosCadastrado();

        // when
        MotoristaResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.versao()).isEqualTo(3L);
    }

    @Test
    void deveDevolverOMotorista_quandoBuscaCnhExistente() {
        // given
        when(repository.findByCnh("111")).thenReturn(Optional.of(carlos()));

        // when
        MotoristaResponseDTO resposta = service.buscarPorCnh("111");

        // then
        assertThat(resposta.nome()).isEqualTo("Carlos");
    }

    // ----- cadastro -----

    @Test
    void naoDeveGravar_quandoCnhJaEstaCadastrada() {
        // given
        when(repository.existsByCnh("111")).thenReturn(true);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto("Outro", "111", null, null)));

        // then
        assertThat(erro).isInstanceOf(CnhJaCadastradaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveGravar_quandoCadastroEhValido() {
        // given
        when(repository.existsByCnh("222")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        MotoristaResponseDTO resposta = service.criar(dto("Dora", "222", "45", null));

        // then
        assertThat(resposta.cnh()).isEqualTo("222");
        verify(repository).save(any());
    }

    // ----- edicao -----

    @Test
    void deveLancarMotoristaNaoEncontrado_quandoPutEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, dto("X", "1", null, null)));

        // then
        assertThat(erro).isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void deveLancarMotoristaNaoEncontrado_quandoPatchEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(9L, dto(null, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void deveLancarConflitoDeVersao_quandoPutTrazVersaoVelha() {
        // given
        carlosCadastrado();

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto("Carlos", "111", null, 2L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveLancarConflitoDeVersao_quandoPatchTrazVersaoVelha() {
        // given
        carlosCadastrado();

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, dto("Carlos", null, null, 2L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveLancarCnhJaCadastrada_quandoPutTrocaParaCnhDeOutro() {
        // given
        carlosCadastrado();
        when(repository.existsByCnh("999")).thenReturn(true);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto("Carlos", "999", null, null)));

        // then
        assertThat(erro).isInstanceOf(CnhJaCadastradaException.class);
    }

    @Test
    void deveLancarCnhJaCadastrada_quandoPatchTrocaParaCnhDeOutro() {
        // given
        carlosCadastrado();
        when(repository.existsByCnh("999")).thenReturn(true);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, dto(null, "999", null, null)));

        // then
        assertThat(erro).isInstanceOf(CnhJaCadastradaException.class);
    }

    @Test
    void deveTrocarACnh_quandoPutUsaCnhLivre() {
        // given
        carlosCadastrado();
        when(repository.existsByCnh("222")).thenReturn(false);

        // when
        MotoristaResponseDTO resposta = service.atualizar(1L, dto("Carlos", "222", null, 3L));

        // then
        assertThat(resposta.cnh()).isEqualTo("222");
    }

    @Test
    void naoDeveConsultarDuplicidade_quandoPutMantemAMesmaCnh() {
        // given
        carlosCadastrado();

        // when
        MotoristaResponseDTO resposta = service.atualizar(1L, dto("Carlos Silva", "111", null, 3L));

        // then
        assertThat(resposta.nome()).isEqualTo("Carlos Silva");
        verify(repository, never()).existsByCnh(any());
    }

    @Test
    void naoDeveConsultarDuplicidade_quandoPatchMantemAMesmaCnh() {
        // given
        carlosCadastrado();

        // when
        MotoristaResponseDTO resposta = service.atualizarParcial(1L, dto(null, "111", null, null));

        // then
        assertThat(resposta.cnh()).isEqualTo("111");
        verify(repository, never()).existsByCnh(any());
    }

    @Test
    void deveAlterarTodosOsCampos_quandoPatchTrazTodos() {
        // given
        carlosCadastrado();
        when(repository.existsByCnh("333")).thenReturn(false);

        // when
        MotoristaResponseDTO resposta = service.atualizarParcial(1L, new MotoristaRequestDTO("Carlos S", "333", "46", -25.0, -54.0, null));

        // then
        assertThat(resposta.nome()).isEqualTo("Carlos S");
        assertThat(resposta.cnh()).isEqualTo("333");
        assertThat(resposta.telefone()).isEqualTo("46");
        assertThat(resposta.latitudeAtual()).isEqualTo(-25.0);
        assertThat(resposta.longitudeAtual()).isEqualTo(-54.0);
    }

    @Test
    void naoDeveMudarNada_quandoPatchVemVazio() {
        // given
        carlosCadastrado();

        // when
        MotoristaResponseDTO resposta = service.atualizarParcial(1L, dto(null, null, null, null));

        // then
        assertThat(resposta.nome()).isEqualTo("Carlos");
        assertThat(resposta.cnh()).isEqualTo("111");
        assertThat(resposta.telefone()).isEqualTo("45");
    }

    // ----- exclusao -----

    @Test
    void deveLancarMotoristaNaoEncontradoSemApagar_quandoExcluiIdInexistente() {
        // given
        when(repository.existsById(9L)).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro).isInstanceOf(MotoristaNaoEncontradoException.class);
        verify(repository, never()).deleteById(9L);
    }

    @Test
    void deveApagar_quandoExcluiIdExistente() {
        // given
        when(repository.existsById(1L)).thenReturn(true);

        // when
        service.deletar(1L);

        // then
        verify(repository).deleteById(1L);
    }
}
