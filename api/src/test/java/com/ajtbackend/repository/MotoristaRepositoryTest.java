package com.ajtbackend.repository;

import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/*
 * o que testa: a busca de motorista pela cnh (achar e nao achar), a checagem
 * de existencia, a unicidade da cnh no banco e a exclusao de motorista que
 * ainda tem ordem de servico (FK).
 *
 * como rodar: precisa de docker (herda de RepositorioTestBase). "mvn test -Dtest=MotoristaRepositoryTest".
 *
 * por que existe: a regra "cnh unica" e a "nao excluir motorista em uso"
 * existem no service, mas a garantia final e da constraint do postgres.
 * este teste prova que o banco realmente segura as duas coisas.
 */
class MotoristaRepositoryTest extends RepositorioTestBase {

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private OrdemServicoRepository ordemServicoRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void deveAcharMotorista_quandoCnhExiste() {
        // given
        motoristaRepository.save(motorista("Joao", "cnh-100"));

        // when
        Optional<Motorista> achado = motoristaRepository.findByCnh("cnh-100");

        // then
        assertThat(achado).isPresent();
        assertThat(achado.get().getNome()).isEqualTo("Joao");
    }

    @Test
    void deveRetornarVazio_quandoCnhNaoExiste() {
        // given
        motoristaRepository.save(motorista("Joao", "cnh-100"));

        // when
        Optional<Motorista> achado = motoristaRepository.findByCnh("cnh-999");

        // then
        assertThat(achado).isEmpty();
    }

    @Test
    void deveRetornarTrue_quandoCnhJaCadastrada() {
        // given
        motoristaRepository.save(motorista("Joao", "cnh-100"));

        // when
        boolean existe = motoristaRepository.existsByCnh("cnh-100");

        // then
        assertThat(existe).isTrue();
    }

    @Test
    void deveRetornarFalse_quandoCnhNaoCadastrada() {
        // given
        motoristaRepository.save(motorista("Joao", "cnh-100"));

        // when
        boolean existe = motoristaRepository.existsByCnh("cnh-999");

        // then
        assertThat(existe).isFalse();
    }

    @Test
    void deveRecusarCnhDuplicada_quandoOutroMotoristaJaTemAMesma() {
        // given
        motoristaRepository.saveAndFlush(motorista("Joao", "cnh-100"));

        // when
        Throwable erro = catchThrowable(() -> motoristaRepository.saveAndFlush(motorista("Maria", "cnh-100")));

        // then
        assertThat(erro).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRecusarExclusao_quandoMotoristaTemOrdemDeServico() {
        // given
        Motorista motorista = motoristaRepository.saveAndFlush(motorista("Joao", "cnh-100"));
        ordemServicoRepository.saveAndFlush(OrdemServico.builder()
                .dataServico(LocalDate.of(2026, 10, 1))
                .motorista(motorista)
                .build());
        // sem o clear o proprio hibernate barra antes (TransientObjectException); queremos a FK do postgres
        em.clear();

        // when
        Throwable erro = catchThrowable(() -> excluirAgora(motorista.getId()));

        // then
        assertThat(erro)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ordens_servico");
    }

    @Test
    void deveExcluirMotorista_quandoNaoTemOrdemDeServico() {
        // given
        Long id = motoristaRepository.saveAndFlush(motorista("Joao", "cnh-100")).getId();
        em.clear();

        // when
        excluirAgora(id);

        // then
        assertThat(motoristaRepository.findById(id)).isEmpty();
    }

    private void excluirAgora(Long id) {
        motoristaRepository.deleteById(id);
        motoristaRepository.flush();
    }

    private Motorista motorista(String nome, String cnh) {
        return Motorista.builder().nome(nome).cnh(cnh).telefone("11999990000").build();
    }
}
