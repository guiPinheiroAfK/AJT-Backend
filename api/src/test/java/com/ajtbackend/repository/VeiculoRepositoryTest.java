package com.ajtbackend.repository;

import com.ajtbackend.model.Veiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/*
 * o que testa: a busca de veiculo pela placa (achar e nao achar), a checagem
 * de existencia usada na validacao de cadastro duplicado e a constraint de
 * placa unica no banco.
 *
 * como rodar: precisa de docker (herda de RepositorioTestBase). "mvn test -Dtest=VeiculoRepositoryTest".
 *
 * por que existe: a placa e unica no banco e o service confere antes de
 * gravar. este teste garante que a consulta de placa acha exatamente o
 * veiculo certo e nao confunde placas parecidas.
 */
class VeiculoRepositoryTest extends RepositorioTestBase {

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Test
    void deveAcharVeiculo_quandoPlacaExiste() {
        // given
        veiculoRepository.save(veiculo("ABC1D23"));

        // when
        Optional<Veiculo> achado = veiculoRepository.findByPlaca("ABC1D23");

        // then
        assertThat(achado).isPresent();
        assertThat(achado.get().getCapacidade()).isEqualTo(44);
    }

    @Test
    void deveRetornarVazio_quandoPlacaNaoExiste() {
        // given
        veiculoRepository.save(veiculo("ABC1D23"));

        // when
        Optional<Veiculo> achado = veiculoRepository.findByPlaca("ABC1D24");

        // then
        assertThat(achado).isEmpty();
    }

    @Test
    void deveRetornarTrue_quandoPlacaJaCadastrada() {
        // given
        veiculoRepository.save(veiculo("ABC1D23"));

        // when
        boolean existe = veiculoRepository.existsByPlaca("ABC1D23");

        // then
        assertThat(existe).isTrue();
    }

    @Test
    void deveRetornarFalse_quandoPlacaNaoCadastrada() {
        // given
        veiculoRepository.save(veiculo("ABC1D23"));

        // when
        boolean existe = veiculoRepository.existsByPlaca("XYZ9W99");

        // then
        assertThat(existe).isFalse();
    }

    @Test
    void deveRecusarPlacaDuplicada_quandoOutroVeiculoJaTemAMesma() {
        // given
        veiculoRepository.saveAndFlush(veiculo("ABC1D23"));

        // when
        Throwable erro = catchThrowable(() -> veiculoRepository.saveAndFlush(veiculo("ABC1D23")));

        // then
        assertThat(erro).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Veiculo veiculo(String placa) {
        return Veiculo.builder()
                .label("Onibus 1")
                .placa(placa)
                .capacidade(44)
                .tipo("Onibus")
                .marca("Marcopolo")
                .build();
    }
}
