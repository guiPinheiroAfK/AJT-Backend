package com.AJTBackend.repository;

import com.AJTBackend.model.Passageiro;
import com.AJTBackend.model.Transfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: o PassageiroRepository no banco real — a @Query JPQL que lista os passageiros
 * de um transfer (a relacao N:N fica do lado do Transfer) em ordem de nome, a busca por
 * nacionalidade sem diferenciar maiusculas e o documento gravado cifrado na coluna (RNF007).
 *
 * como rodar: @DataJpaTest com PostgreSQL via Testcontainers (RepositorioTestBase).
 * precisa de Docker; sem Docker a classe e pulada.
 *
 * por que existe: JPQL so e validada de verdade contra o banco, e o documento em texto puro
 * na coluna seria vazamento de dado pessoal mesmo com a API mascarando na leitura.
 */
class PassageiroRepositoryTest extends RepositorioTestBase {

    private static final String CPF = "529.982.247-25";

    @Autowired private TestEntityManager em;
    @Autowired private PassageiroRepository repository;

    private Passageiro passageiro(String nome, String nacionalidade) {
        return em.persist(Passageiro.builder().nome(nome).tipoDocumento("PASSAPORTE")
                .documento("DOC-" + nome).nacionalidade(nacionalidade).build());
    }

    @Test
    void passageirosDeUmTransferVemEmOrdemDeNomeESoOsDele() {
        // Given
        Passageiro bia = passageiro("Bia", "Brasileira");
        Passageiro ana = passageiro("Ana", "Brasileira");
        Passageiro carlos = passageiro("Carlos", "Chilena");
        Transfer transfer = em.persist(Transfer.builder().dataTransfer(LocalDate.of(2031, 3, 10))
                .horaTransfer(LocalTime.NOON).origem("GRU").destino("Hotel").passageiros(Set.of(bia, ana)).build());
        em.persist(Transfer.builder().dataTransfer(LocalDate.of(2031, 3, 10)).horaTransfer(LocalTime.NOON)
                .origem("GRU").destino("Centro").passageiros(Set.of(carlos)).build());
        em.flush();

        // When
        List<Passageiro> doTransfer = repository.findByTransferId(transfer.getId());

        // Then
        assertThat(doTransfer).extracting(Passageiro::getNome).containsExactly("Ana", "Bia");
    }

    @Test
    void buscaPorNacionalidadeIgnoraMaiusculas() {
        // Given
        passageiro("Ana", "Brasileira");
        passageiro("John", "Americana");
        em.flush();

        // When
        Page<Passageiro> brasileiros = repository.findByNacionalidadeIgnoreCase("BRASILEIRA", PageRequest.of(0, 10));

        // Then
        assertThat(brasileiros.getContent()).extracting(Passageiro::getNome).containsExactly("Ana");
    }

    @Test
    void documentoFicaCifradoNaColunaEVoltaLegivelNaEntidade() {
        // Given
        Long id = em.persistAndGetId(Passageiro.builder().nome("Ana").tipoDocumento("CPF").documento(CPF).build(), Long.class);
        em.flush();
        em.clear();

        // When
        String naColuna = (String) em.getEntityManager()
                .createNativeQuery("select documento from passageiros where id = :id")
                .setParameter("id", id)
                .getSingleResult();
        Passageiro lido = repository.findById(id).orElseThrow();

        // Then
        assertThat(naColuna).startsWith("v1:").doesNotContain(CPF).doesNotContain("52998224725");
        assertThat(lido.getDocumento()).isEqualTo(CPF);
    }
}
