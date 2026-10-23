package com.AJTBackend.service;

import com.AJTBackend.dto.FiltroMotoristaDTO;
import com.AJTBackend.dto.MotoristaRequestDTO;
import com.AJTBackend.dto.MotoristaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.exception.CnhJaCadastradaException;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.repository.MotoristaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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

    @Test
    @SuppressWarnings("unchecked")
    void listaComFiltroDevolveAPagina() {
        // Given
        Pageable pagina = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(carlos()), pagina, 1));

        // When
        PaginaResponseDTO<MotoristaResponseDTO> resultado = service.listar(new FiltroMotoristaDTO("car", null), pagina);

        // Then
        assertThat(resultado.conteudo()).extracting(MotoristaResponseDTO::nome).containsExactly("Carlos");
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void buscaPorIdDevolveOMotoristaComAVersao() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));

        // When
        MotoristaResponseDTO encontrado = service.buscarPorId(1L);

        // Then
        assertThat(encontrado.nome()).isEqualTo("Carlos");
        assertThat(encontrado.versao()).isEqualTo(3L);
    }

    @Test
    void buscaPorCnhDevolveOMotorista() {
        // Given
        when(repository.findByCnh("111")).thenReturn(Optional.of(carlos()));

        // When
        MotoristaResponseDTO encontrado = service.buscarPorCnh("111");

        // Then
        assertThat(encontrado.nome()).isEqualTo("Carlos");
        assertThat(encontrado.cnh()).isEqualTo("111");
    }

    @Test
    void buscaPorIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L))
                .isInstanceOf(MotoristaNaoEncontradoException.class)
                .hasMessageContaining("9");
    }

    @Test
    void buscaPorCnhInexistenteLanca404() {
        // Given
        when(repository.findByCnh("000")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorCnh("000"))
                .isInstanceOf(MotoristaNaoEncontradoException.class)
                .hasMessageContaining("000");
    }

    @Test
    void cadastroComCnhRepetidaLanca400SemGravar() {
        // Given
        when(repository.existsByCnh("111")).thenReturn(true);
        MotoristaRequestDTO dto = dto("Outro", "111", null, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto)).isInstanceOf(CnhJaCadastradaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroValidoGravaOsDadosInformados() {
        // Given
        when(repository.existsByCnh("222")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        MotoristaResponseDTO criado = service.criar(dto("Dora", "222", "45", null));

        // Then
        assertThat(criado.nome()).isEqualTo("Dora");
        assertThat(criado.cnh()).isEqualTo("222");
        assertThat(criado.telefone()).isEqualTo("45");
        verify(repository).save(any(Motorista.class));
    }

    @Test
    void putDeIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        MotoristaRequestDTO dto = dto("X", "1", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto)).isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void patchDeIdInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        MotoristaRequestDTO dto = dto(null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto)).isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void putComVersaoVelhaLanca409() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        MotoristaRequestDTO versaoVelha = dto("Carlos", "111", null, 2L);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void patchComVersaoVelhaLanca409() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        MotoristaRequestDTO versaoVelha = dto("Carlos", null, null, 2L);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void putParaCnhDeOutroMotoristaLanca400() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        when(repository.existsByCnh("999")).thenReturn(true);
        MotoristaRequestDTO dto = dto("Carlos", "999", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto)).isInstanceOf(CnhJaCadastradaException.class);
    }

    @Test
    void patchParaCnhDeOutroMotoristaLanca400() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        when(repository.existsByCnh("999")).thenReturn(true);
        MotoristaRequestDTO dto = dto(null, "999", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto)).isInstanceOf(CnhJaCadastradaException.class);
    }

    @Test
    void putParaUmaCnhLivreTrocaACnh() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        when(repository.existsByCnh("222")).thenReturn(false);

        // When
        MotoristaResponseDTO atualizado = service.atualizar(1L, dto("Carlos", "222", null, 3L));

        // Then
        assertThat(atualizado.cnh()).isEqualTo("222");
        verify(repository).flush();
    }

    @Test
    void putMantendoAMesmaCnhNaoConsultaDuplicidade() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));

        // When
        MotoristaResponseDTO atualizado = service.atualizar(1L, dto("Carlos Silva", "111", null, 3L));

        // Then
        assertThat(atualizado.nome()).isEqualTo("Carlos Silva");
        verify(repository, never()).existsByCnh(any());
    }

    @Test
    void patchMantendoAMesmaCnhNaoConsultaDuplicidade() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));

        // When
        MotoristaResponseDTO atualizado = service.atualizarParcial(1L, dto(null, "111", null, null));

        // Then
        assertThat(atualizado.cnh()).isEqualTo("111");
        verify(repository, never()).existsByCnh(any());
    }

    @Test
    void patchComTodosOsCamposTrocaTudo() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        when(repository.existsByCnh("333")).thenReturn(false);
        MotoristaRequestDTO patch = new MotoristaRequestDTO("Carlos S", "333", "46", -25.0, -54.0, null);

        // When
        MotoristaResponseDTO resposta = service.atualizarParcial(1L, patch);

        // Then
        assertThat(resposta.nome()).isEqualTo("Carlos S");
        assertThat(resposta.cnh()).isEqualTo("333");
        assertThat(resposta.telefone()).isEqualTo("46");
        assertThat(resposta.latitudeAtual()).isEqualTo(-25.0);
        assertThat(resposta.longitudeAtual()).isEqualTo(-54.0);
    }

    @Test
    void patchVazioNaoMudaNada() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));

        // When
        MotoristaResponseDTO resposta = service.atualizarParcial(1L, dto(null, null, null, null));

        // Then
        assertThat(resposta.nome()).isEqualTo("Carlos");
        assertThat(resposta.cnh()).isEqualTo("111");
        assertThat(resposta.telefone()).isEqualTo("45");
    }

    @Test
    void excluirMotoristaExistenteApaga() {
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
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(MotoristaNaoEncontradoException.class);
        verify(repository, never()).deleteById(any());
    }
}
