package com.AJTBackend.service;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.VeiculoRequestDTO;
import com.AJTBackend.dto.VeiculoResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.PlacaJaCadastradaException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void listarTodosDevolveAPaginaConvertida() {
        // Given
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(van()), pagina, 1));

        // When
        PaginaResponseDTO<VeiculoResponseDTO> resultado = service.listarTodos(pagina);

        // Then
        assertThat(resultado.totalElementos()).isEqualTo(1);
        assertThat(resultado.conteudo()).extracting(VeiculoResponseDTO::placa).containsExactly("AAA1A11");
    }

    @Test
    void buscaPorIdDevolveOVeiculoComAVersao() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));

        // When
        VeiculoResponseDTO encontrado = service.buscarPorId(1L);

        // Then
        assertThat(encontrado.label()).isEqualTo("Van 01");
        assertThat(encontrado.versao()).isEqualTo(2L);
    }

    @Test
    void buscaPorPlacaDevolveOVeiculo() {
        // Given
        when(repository.findByPlaca("AAA1A11")).thenReturn(Optional.of(van()));

        // When
        VeiculoResponseDTO encontrado = service.buscarPorPlaca("AAA1A11");

        // Then
        assertThat(encontrado.label()).isEqualTo("Van 01");
        assertThat(encontrado.capacidade()).isEqualTo(15);
    }

    @Test
    void buscaPorIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(VeiculoNaoEncontradoException.class);
    }

    @Test
    void buscaPorPlacaInexistenteLanca404ComAPlaca() {
        // Given
        when(repository.findByPlaca("ZZZ")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorPlaca("ZZZ"))
                .isInstanceOf(VeiculoNaoEncontradoException.class)
                .hasMessageContaining("ZZZ");
    }

    @Test
    void putDeIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        VeiculoRequestDTO dto = dto("V", "P", 1, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto)).isInstanceOf(VeiculoNaoEncontradoException.class);
    }

    @Test
    void patchDeIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        VeiculoRequestDTO dto = dto(null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto)).isInstanceOf(VeiculoNaoEncontradoException.class);
    }

    @Test
    void placaRepetidaNoCadastroLanca400SemGravar() {
        // Given
        when(repository.existsByPlaca("AAA1A11")).thenReturn(true);
        VeiculoRequestDTO dto = dto("Van 02", "AAA1A11", 10, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto)).isInstanceOf(PlacaJaCadastradaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroValidoGravaOsDadosInformados() {
        // Given
        when(repository.existsByPlaca("BBB2B22")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        VeiculoResponseDTO criado = service.criar(dto("Van 02", "BBB2B22", 10, null));

        // Then
        assertThat(criado.label()).isEqualTo("Van 02");
        assertThat(criado.placa()).isEqualTo("BBB2B22");
        assertThat(criado.capacidade()).isEqualTo(10);
        verify(repository).save(any(Veiculo.class));
    }

    @Test
    void putComVersaoVelhaLanca409() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        VeiculoRequestDTO versaoVelha = dto("Van", "AAA1A11", 15, 1L);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void patchComVersaoVelhaLanca409() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        VeiculoRequestDTO versaoVelha = dto(null, null, null, 1L);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void putParaPlacaDeOutroVeiculoLanca400() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.existsByPlaca("CCC3C33")).thenReturn(true);
        VeiculoRequestDTO dto = dto("Van", "CCC3C33", 15, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto)).isInstanceOf(PlacaJaCadastradaException.class);
    }

    @Test
    void patchParaPlacaDeOutroVeiculoLanca400() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.existsByPlaca("CCC3C33")).thenReturn(true);
        VeiculoRequestDTO dto = dto(null, "CCC3C33", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto)).isInstanceOf(PlacaJaCadastradaException.class);
    }

    @Test
    void putParaUmaPlacaLivreTrocaAPlaca() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.existsByPlaca("BBB2B22")).thenReturn(false);

        // When
        VeiculoResponseDTO atualizado = service.atualizar(1L, dto("Van 01", "BBB2B22", 15, 2L));

        // Then
        assertThat(atualizado.placa()).isEqualTo("BBB2B22");
        verify(repository).flush();
    }

    @Test
    void putMantendoAMesmaPlacaNaoConsultaDuplicidade() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));

        // When
        VeiculoResponseDTO atualizado = service.atualizar(1L, dto("Van Nova", "AAA1A11", 16, 2L));

        // Then
        assertThat(atualizado.capacidade()).isEqualTo(16);
        verify(repository, never()).existsByPlaca(any());
    }

    @Test
    void patchMantendoAMesmaPlacaNaoConsultaDuplicidade() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));

        // When
        VeiculoResponseDTO atualizado = service.atualizarParcial(1L, dto(null, "AAA1A11", null, null));

        // Then
        assertThat(atualizado.placa()).isEqualTo("AAA1A11");
        verify(repository, never()).existsByPlaca(any());
    }

    @Test
    void patchComTodosOsCamposTrocaTudo() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.existsByPlaca("DDD4D44")).thenReturn(false);
        VeiculoRequestDTO patch = new VeiculoRequestDTO("Van 9", "DDD4D44", 20, "VAN", "Fiat", null);

        // When
        VeiculoResponseDTO resposta = service.atualizarParcial(1L, patch);

        // Then
        assertThat(resposta.label()).isEqualTo("Van 9");
        assertThat(resposta.placa()).isEqualTo("DDD4D44");
        assertThat(resposta.capacidade()).isEqualTo(20);
        assertThat(resposta.tipo()).isEqualTo("VAN");
        assertThat(resposta.marca()).isEqualTo("Fiat");
    }

    @Test
    void excluirVeiculoExistenteApaga() {
        // Given
        when(repository.existsById(1L)).thenReturn(true);

        // When
        service.deletar(1L);

        // Then
        verify(repository).deleteById(1L);
    }

    @Test
    void excluirInexistenteLanca404SemApagar() {
        // Given
        when(repository.existsById(9L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(VeiculoNaoEncontradoException.class);
        verify(repository, never()).deleteById(any());
    }
}
