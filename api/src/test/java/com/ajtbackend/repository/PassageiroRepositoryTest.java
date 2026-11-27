package com.ajtbackend.repository;

import com.ajtbackend.model.Passageiro;
import com.ajtbackend.model.Transfer;
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
    private static final LocalDate DIA = LocalDate.of(2031, 3, 10);

    @Autowired private TestEntityManager em;
    @Autowired private PassageiroRepository repository;

    private Passageiro passageiro(String nome, String nacionalidade) {
        return em.persist(Passageiro.builder().nome(nome).tipoDocumento("PASSAPORTE")
                .documento("DOC-" + nome).nacionalidade(nacionalidade).build());
    }

    private Transfer transferCom(Set<Passageiro> passageiros) {
        return em.persist(Transfer.builder().dataTransfer(DIA).horaTransfer(LocalTime.NOON)
                .origem("GRU").destino("Hotel").passageiros(passageiros).build());
    }

    // grava o passageiro com o CPF e limpa a sessao, pra leitura seguinte ir ao banco
    private Long passageiroComCpfGravado() {
        Long id = em.persistAndGetId(Passageiro.builder().nome("Ana").tipoDocumento("CPF").documento(CPF).build(), Long.class);
        em.flush();
        em.clear();
        return id;
    }

    // ----- passageiros de um transfer -----

    @Test
    void deveListarSoOsDoTransferEmOrdemDeNome_quandoOutroTransferTemOutroPassageiro() {
        // given
        Passageiro bia = passageiro("Bia", "Brasileira");
        Passageiro ana = passageiro("Ana", "Brasileira");
        Passageiro carlos = passageiro("Carlos", "Chilena");
        Transfer transfer = transferCom(Set.of(bia, ana));
        transferCom(Set.of(carlos));
        em.flush();

        // when
        List<Passageiro> doTransfer = repository.findByTransferId(transfer.getId());

        // then
        assertThat(doTransfer).extracting(Passageiro::getNome).containsExactly("Ana", "Bia");
    }

    @Test
    void deveDevolverListaVazia_quandoTransferNaoTemPassageiros() {
        // given
        Transfer semPassageiros = transferCom(Set.of());
        em.flush();

        // when
        List<Passageiro> doTransfer = repository.findByTransferId(semPassageiros.getId());

        // then
        assertThat(doTransfer).isEmpty();
    }

    @Test
    void deveDevolverListaVazia_quandoTransferNaoExiste() {
        // when
        List<Passageiro> doTransfer = repository.findByTransferId(999_999L);

        // then
        assertThat(doTransfer).isEmpty();
    }

    // ----- nacionalidade -----

    @Test
    void deveAcharSemDiferenciarMaiusculas_quandoBuscaNacionalidadeEmMaiusculo() {
        // given
        passageiro("Ana", "Brasileira");
        passageiro("John", "Americana");
        em.flush();

        // when
        Page<Passageiro> brasileiros = repository.findByNacionalidadeIgnoreCase("BRASILEIRA", PageRequest.of(0, 10));

        // then
        assertThat(brasileiros.getContent()).extracting(Passageiro::getNome).containsExactly("Ana");
    }

    @Test
    void deveDevolverPaginaVazia_quandoNacionalidadeNaoExiste() {
        // given
        passageiro("Ana", "Brasileira");
        em.flush();

        // when
        Page<Passageiro> japoneses = repository.findByNacionalidadeIgnoreCase("Japonesa", PageRequest.of(0, 10));

        // then
        assertThat(japoneses.getTotalElements()).isZero();
    }

    // ----- documento cifrado -----

    @Test
    void deveGravarCifradoNaColuna_quandoPassageiroTemCpf() {
        // given
        Long id = passageiroComCpfGravado();

        // when
        String naColuna = (String) em.getEntityManager()
                .createNativeQuery("select documento from passageiros where id = :id")
                .setParameter("id", id)
                .getSingleResult();

        // then
        assertThat(naColuna).startsWith("v1:").doesNotContain(CPF).doesNotContain("52998224725");
    }

    @Test
    void deveDevolverODocumentoLegivel_quandoLeOPassageiroDoBanco() {
        // given
        Long id = passageiroComCpfGravado();

        // when
        Passageiro lido = repository.findById(id).orElseThrow();

        // then
        assertThat(lido.getDocumento()).isEqualTo(CPF);
    }
}
