package com.ajtbackend.service;

import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.dto.VeiculoRequestDTO;
import com.ajtbackend.dto.VeiculoResponseDTO;
import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.PlacaJaCadastradaException;
import com.ajtbackend.exception.VeiculoNaoEncontradoException;
import com.ajtbackend.model.Veiculo;
import com.ajtbackend.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
 * o que testa: as regras do VeiculoService — placa unica no cadastro e na edicao (PUT e
 * PATCH, inclusive mantendo a mesma placa), id e placa inexistentes (404), versao velha
 * (409), PATCH so mexendo no que veio e exclusao de quem nao existe.
 *
 * como rodar: teste unitario com o repositorio mockado. sem spring, sem banco. "mvn test".
 *
 * por que existe: duas vans com a mesma placa quebram a montagem das OS; o teste de web
 * so prova o status, este prova a regra.
 */
class VeiculoServiceTest {

    private final VeiculoRepository repository = mock(VeiculoRepository.class);
    private final VeiculoService service = new VeiculoService(repository);

    private static Veiculo van() {
        return Veiculo.builder().id(1L).versao(2L).label("Van 01").placa("AAA1A11").capacidade(15).build();
    }

    private static VeiculoRequestDTO dto(String label, String placa, Integer capacidade, Long versao) {
        return new VeiculoRequestDTO(label, placa, capacidade, null, null, versao);
    }

    private void vanCadastrada() {
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
    }

    // ----- leitura -----

    @Test
    void deveDevolverAPagina_quandoListaTodos() {
        // given
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(van()), pagina, 1));

        // when
        PaginaResponseDTO<VeiculoResponseDTO> resultado = service.listarTodos(pagina);

        // then
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void deveDevolverComAVersao_quandoBuscaIdExistente() {
        // given
        vanCadastrada();

        // when
        VeiculoResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.versao()).isEqualTo(2L);
    }

    @Test
    void deveDevolverOVeiculo_quandoBuscaPlacaExistente() {
        // given
        when(repository.findByPlaca("AAA1A11")).thenReturn(Optional.of(van()));

        // when
        VeiculoResponseDTO resposta = service.buscarPorPlaca("AAA1A11");

        // then
        assertThat(resposta.label()).isEqualTo("Van 01");
    }

    @Test
    void deveLancarVeiculoNaoEncontrado_quandoIdNaoExiste() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro).isInstanceOf(VeiculoNaoEncontradoException.class);
    }

    @Test
    void deveLancarVeiculoNaoEncontrado_quandoPlacaNaoExiste() {
        // given
        when(repository.findByPlaca("ZZZ")).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorPlaca("ZZZ"));

        // then
        assertThat(erro).isInstanceOf(VeiculoNaoEncontradoException.class).hasMessageContaining("ZZZ");
    }

    // ----- cadastro -----

    @Test
    void naoDeveGravar_quandoPlacaJaEstaCadastrada() {
        // given
        when(repository.existsByPlaca("AAA1A11")).thenReturn(true);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto("Van 02", "AAA1A11", 10, null)));

        // then
        assertThat(erro).isInstanceOf(PlacaJaCadastradaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveGravar_quandoCadastroEhValido() {
        // given
        when(repository.existsByPlaca("BBB2B22")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        VeiculoResponseDTO resposta = service.criar(dto("Van 02", "BBB2B22", 10, null));

        // then
        assertThat(resposta.placa()).isEqualTo("BBB2B22");
        verify(repository).save(any());
    }

    // ----- edicao -----

    @Test
    void deveLancarVeiculoNaoEncontrado_quandoPutEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, dto("V", "P", 1, null)));

        // then
        assertThat(erro).isInstanceOf(VeiculoNaoEncontradoException.class);
    }

    @Test
    void deveLancarVeiculoNaoEncontrado_quandoPatchEmIdInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(9L, dto(null, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(VeiculoNaoEncontradoException.class);
    }

    @Test
    void deveLancarConflitoDeVersao_quandoPutTrazVersaoVelha() {
        // given
        vanCadastrada();

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto("Van", "AAA1A11", 15, 1L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveLancarConflitoDeVersao_quandoPatchTrazVersaoVelha() {
        // given
        vanCadastrada();

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, dto(null, null, null, 1L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveLancarPlacaJaCadastrada_quandoPutTrocaParaPlacaDeOutro() {
        // given
        vanCadastrada();
        when(repository.existsByPlaca("CCC3C33")).thenReturn(true);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto("Van", "CCC3C33", 15, null)));

        // then
        assertThat(erro).isInstanceOf(PlacaJaCadastradaException.class);
    }

    @Test
    void deveLancarPlacaJaCadastrada_quandoPatchTrocaParaPlacaDeOutro() {
        // given
        vanCadastrada();
        when(repository.existsByPlaca("CCC3C33")).thenReturn(true);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, dto(null, "CCC3C33", null, null)));

        // then
        assertThat(erro).isInstanceOf(PlacaJaCadastradaException.class);
    }

    @Test
    void deveTrocarAPlaca_quandoPutUsaPlacaLivre() {
        // given
        vanCadastrada();
        when(repository.existsByPlaca("BBB2B22")).thenReturn(false);

        // when
        VeiculoResponseDTO resposta = service.atualizar(1L, dto("Van 01", "BBB2B22", 15, 2L));

        // then
        assertThat(resposta.placa()).isEqualTo("BBB2B22");
    }

    @Test
    void naoDeveConsultarDuplicidade_quandoPutMantemAMesmaPlaca() {
        // given
        vanCadastrada();

        // when
        VeiculoResponseDTO resposta = service.atualizar(1L, dto("Van Nova", "AAA1A11", 16, 2L));

        // then
        assertThat(resposta.capacidade()).isEqualTo(16);
        verify(repository, never()).existsByPlaca(any());
    }

    @Test
    void naoDeveConsultarDuplicidade_quandoPatchMantemAMesmaPlaca() {
        // given
        vanCadastrada();

        // when
        VeiculoResponseDTO resposta = service.atualizarParcial(1L, dto(null, "AAA1A11", null, null));

        // then
        assertThat(resposta.placa()).isEqualTo("AAA1A11");
        verify(repository, never()).existsByPlaca(any());
    }

    @Test
    void deveAlterarTodosOsCampos_quandoPatchTrazTodos() {
        // given
        vanCadastrada();
        when(repository.existsByPlaca("DDD4D44")).thenReturn(false);

        // when
        VeiculoResponseDTO resposta = service.atualizarParcial(1L, new VeiculoRequestDTO("Van 9", "DDD4D44", 20, "VAN", "Fiat", null));

        // then
        assertThat(resposta.label()).isEqualTo("Van 9");
        assertThat(resposta.placa()).isEqualTo("DDD4D44");
        assertThat(resposta.capacidade()).isEqualTo(20);
        assertThat(resposta.tipo()).isEqualTo("VAN");
        assertThat(resposta.marca()).isEqualTo("Fiat");
    }

    // ----- exclusao -----

    @Test
    void deveLancarVeiculoNaoEncontradoSemApagar_quandoExcluiIdInexistente() {
        // given
        when(repository.existsById(9L)).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro).isInstanceOf(VeiculoNaoEncontradoException.class);
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
