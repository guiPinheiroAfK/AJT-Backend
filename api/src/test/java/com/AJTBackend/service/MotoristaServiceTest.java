package com.AJTBackend.service;

import com.AJTBackend.dto.FiltroMotoristaDTO;
import com.AJTBackend.dto.MotoristaRequestDTO;
import com.AJTBackend.dto.MotoristaResponseDTO;
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
    void listaComFiltroDevolveAPagina() {
        Pageable pagina = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(carlos()), pagina, 1));

        var resultado = service.listar(new FiltroMotoristaDTO("car", null), pagina);

        assertThat(resultado.conteudo()).extracting(MotoristaResponseDTO::nome).containsExactly("Carlos");
    }

    @Test
    void buscaPorIdECnhInexistentesLancam404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.findByCnh("000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(MotoristaNaoEncontradoException.class).hasMessageContaining("9");
        assertThatThrownBy(() -> service.buscarPorCnh("000")).isInstanceOf(MotoristaNaoEncontradoException.class).hasMessageContaining("000");
    }

    @Test
    void buscaPorIdECnhExistentes() {
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        when(repository.findByCnh("111")).thenReturn(Optional.of(carlos()));

        assertThat(service.buscarPorId(1L).versao()).isEqualTo(3L);
        assertThat(service.buscarPorCnh("111").nome()).isEqualTo("Carlos");
    }

    @Test
    void cadastroComCnhRepetidaLanca400SemGravar() {
        when(repository.existsByCnh("111")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(dto("Outro", "111", null, null))).isInstanceOf(CnhJaCadastradaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroValidoGrava() {
        when(repository.existsByCnh("222")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.criar(dto("Dora", "222", "45", null)).cnh()).isEqualTo("222");
    }

    @Test
    void edicaoDeIdInexistenteLanca404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(9L, dto("X", "1", null, null))).isInstanceOf(MotoristaNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto(null, null, null, null))).isInstanceOf(MotoristaNaoEncontradoException.class);
    }

    @Test
    void edicaoComVersaoVelhaLanca409() {
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));

        assertThatThrownBy(() -> service.atualizar(1L, dto("Carlos", "111", null, 2L))).isInstanceOf(ConflitoDeVersaoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto("Carlos", null, null, 2L))).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void trocarParaCnhDeOutroMotoristaLanca400NoPutENoPatch() {
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));
        when(repository.existsByCnh("999")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(1L, dto("Carlos", "999", null, null))).isInstanceOf(CnhJaCadastradaException.class);
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto(null, "999", null, null))).isInstanceOf(CnhJaCadastradaException.class);
    }

    @Test
    void manterAMesmaCnhNaoConsultaDuplicidade() {
        when(repository.findById(1L)).thenReturn(Optional.of(carlos()));

        assertThat(service.atualizar(1L, dto("Carlos Silva", "111", null, 3L)).nome()).isEqualTo("Carlos Silva");
        assertThat(service.atualizarParcial(1L, dto(null, "111", null, null)).cnh()).isEqualTo("111");
        verify(repository, never()).existsByCnh(any());
    }

    @Test
    void patchMudaSoOQueVeio() {
        Motorista existente = carlos();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.existsByCnh("333")).thenReturn(false);

        var resposta = service.atualizarParcial(1L, new MotoristaRequestDTO("Carlos S", "333", "46", -25.0, -54.0, null));

        assertThat(resposta.nome()).isEqualTo("Carlos S");
        assertThat(resposta.cnh()).isEqualTo("333");
        assertThat(resposta.telefone()).isEqualTo("46");
        assertThat(resposta.latitudeAtual()).isEqualTo(-25.0);
        assertThat(resposta.longitudeAtual()).isEqualTo(-54.0);

        var semNada = service.atualizarParcial(1L, dto(null, null, null, null));
        assertThat(semNada.nome()).isEqualTo("Carlos S"); // nada veio, nada mudou
    }

    @Test
    void excluirInexistenteLanca404EExistenteApaga() {
        when(repository.existsById(9L)).thenReturn(false);
        when(repository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(MotoristaNaoEncontradoException.class);
        service.deletar(1L);
        verify(repository).deleteById(1L);
        verify(repository, never()).deleteById(9L);
    }
}
