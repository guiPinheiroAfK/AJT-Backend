package com.AJTBackend.service;

import com.AJTBackend.dto.VeiculoRequestDTO;
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
    void listaEBuscasExistentes() {
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(van()), pagina, 1));
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.findByPlaca("AAA1A11")).thenReturn(Optional.of(van()));

        assertThat(service.listarTodos(pagina).totalElementos()).isEqualTo(1);
        assertThat(service.buscarPorId(1L).versao()).isEqualTo(2L);
        assertThat(service.buscarPorPlaca("AAA1A11").label()).isEqualTo("Van 01");
    }

    @Test
    void idEPlacaInexistentesLancam404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.findByPlaca("ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(VeiculoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.buscarPorPlaca("ZZZ")).isInstanceOf(VeiculoNaoEncontradoException.class).hasMessageContaining("ZZZ");
        assertThatThrownBy(() -> service.atualizar(9L, dto("V", "P", 1, null))).isInstanceOf(VeiculoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto(null, null, null, null))).isInstanceOf(VeiculoNaoEncontradoException.class);
    }

    @Test
    void placaRepetidaNoCadastroLanca400SemGravar() {
        when(repository.existsByPlaca("AAA1A11")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(dto("Van 02", "AAA1A11", 10, null))).isInstanceOf(PlacaJaCadastradaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroValidoGrava() {
        when(repository.existsByPlaca("BBB2B22")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.criar(dto("Van 02", "BBB2B22", 10, null)).placa()).isEqualTo("BBB2B22");
    }

    @Test
    void versaoVelhaLanca409() {
        when(repository.findById(1L)).thenReturn(Optional.of(van()));

        assertThatThrownBy(() -> service.atualizar(1L, dto("Van", "AAA1A11", 15, 1L))).isInstanceOf(ConflitoDeVersaoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto(null, null, null, 1L))).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void trocarParaPlacaDeOutroVeiculoLanca400() {
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.existsByPlaca("CCC3C33")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(1L, dto("Van", "CCC3C33", 15, null))).isInstanceOf(PlacaJaCadastradaException.class);
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto(null, "CCC3C33", null, null))).isInstanceOf(PlacaJaCadastradaException.class);
    }

    @Test
    void putParaUmaPlacaLivreTrocaAPlaca() {
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.existsByPlaca("BBB2B22")).thenReturn(false);

        assertThat(service.atualizar(1L, dto("Van 01", "BBB2B22", 15, 2L)).placa()).isEqualTo("BBB2B22");
    }

    @Test
    void manterAMesmaPlacaNaoConsultaDuplicidade() {
        when(repository.findById(1L)).thenReturn(Optional.of(van()));

        assertThat(service.atualizar(1L, dto("Van Nova", "AAA1A11", 16, 2L)).capacidade()).isEqualTo(16);
        assertThat(service.atualizarParcial(1L, dto(null, "AAA1A11", null, null)).placa()).isEqualTo("AAA1A11");
        verify(repository, never()).existsByPlaca(any());
    }

    @Test
    void patchMudaSoOQueVeio() {
        when(repository.findById(1L)).thenReturn(Optional.of(van()));
        when(repository.existsByPlaca("DDD4D44")).thenReturn(false);

        var resposta = service.atualizarParcial(1L, new VeiculoRequestDTO("Van 9", "DDD4D44", 20, "VAN", "Fiat", null));

        assertThat(resposta.label()).isEqualTo("Van 9");
        assertThat(resposta.placa()).isEqualTo("DDD4D44");
        assertThat(resposta.capacidade()).isEqualTo(20);
        assertThat(resposta.tipo()).isEqualTo("VAN");
        assertThat(resposta.marca()).isEqualTo("Fiat");
    }

    @Test
    void excluirInexistenteLanca404EExistenteApaga() {
        when(repository.existsById(9L)).thenReturn(false);
        when(repository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(VeiculoNaoEncontradoException.class);
        service.deletar(1L);
        verify(repository).deleteById(1L);
    }
}
