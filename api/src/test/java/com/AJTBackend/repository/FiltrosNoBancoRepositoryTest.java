package com.AJTBackend.repository;

import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Transfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: as Especificacoes (filtros das listagens, RF015) executadas no banco real —
 * "contem" sem diferenciar maiusculas, % e _ digitados tratados como texto (escape do LIKE),
 * busca em mais de um campo, periodo fechado e filtro por campo aninhado.
 *
 * como rodar: @DataJpaTest com PostgreSQL via Testcontainers (RepositorioTestBase).
 * precisa de Docker; sem Docker a classe e pulada. (o EspecificacoesTest confere as mesmas
 * pecas com mocks, sem banco.)
 *
 * por que existe: o escape do LIKE so e provado de verdade pelo banco: se o ESCAPE nao bater
 * com o do Postgres, buscar "%" traria todos os registros.
 */
class FiltrosNoBancoRepositoryTest extends RepositorioTestBase {

    private static final LocalDate DIA = LocalDate.of(2031, 7, 1);

    @Autowired private TestEntityManager em;
    @Autowired private TransferRepository transferRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;

    private void transfer(LocalDate data, String origem, String destino) {
        em.persist(Transfer.builder().dataTransfer(data).horaTransfer(LocalTime.NOON)
                .origem(origem).destino(destino).build());
    }

    private static List<String> origens(List<Transfer> transfers) {
        return transfers.stream().map(Transfer::getOrigem).sorted().toList();
    }

    @Test
    void contemIgnoraMaiusculasETrataPorcentagemComoTexto() {
        // Given
        transfer(DIA, "Promo 100% Aeroporto", "Hotel");
        transfer(DIA, "Aeroporto 100 Norte", "Hotel");
        transfer(DIA, "Rodoviaria", "Hotel");
        em.flush();

        // When
        List<Transfer> comPorcentagem = transferRepository.findAll(Especificacoes.contem("origem", "100%"));
        List<Transfer> soPorcentagem = transferRepository.findAll(Especificacoes.contem("origem", "%"));
        List<Transfer> aeroporto = transferRepository.findAll(Especificacoes.contem("origem", "AEROPORTO"));

        // Then
        assertThat(origens(comPorcentagem)).containsExactly("Promo 100% Aeroporto");
        assertThat(origens(soPorcentagem)).containsExactly("Promo 100% Aeroporto");
        assertThat(origens(aeroporto)).containsExactly("Aeroporto 100 Norte", "Promo 100% Aeroporto");
    }

    @Test
    void sublinhadoDigitadoNaoViraCuringaDeUmCaractere() {
        // Given
        transfer(DIA, "Portao_3", "Hotel");
        transfer(DIA, "PortaoX3", "Hotel");
        em.flush();

        // When
        List<Transfer> encontrados = transferRepository.findAll(Especificacoes.contem("origem", "portao_3"));

        // Then
        assertThat(origens(encontrados)).containsExactly("Portao_3");
    }

    @Test
    void algumContemProcuraNaOrigemOuNoDestino() {
        // Given
        transfer(DIA, "GRU", "Hotel Centro");
        transfer(DIA, "Centro de Convencoes", "GRU");
        transfer(DIA, "Rodoviaria", "Praia");
        em.flush();

        // When
        List<Transfer> encontrados = transferRepository.findAll(Especificacoes.algumContem("centro", "origem", "destino"));

        // Then
        assertThat(origens(encontrados)).containsExactly("Centro de Convencoes", "GRU");
    }

    @Test
    void periodoIncluiAsDuasPontasEDeixaDeForaOResto() {
        // Given
        transfer(DIA.minusDays(1), "Antes", "X");
        transfer(DIA, "Inicio", "X");
        transfer(DIA.plusDays(5), "Fim", "X");
        transfer(DIA.plusDays(6), "Depois", "X");
        em.flush();

        // When
        List<Transfer> noPeriodo = transferRepository.findAll(Especificacoes.dataEntre("dataTransfer", DIA, DIA.plusDays(5)));

        // Then
        assertThat(origens(noPeriodo)).containsExactly("Fim", "Inicio");
    }

    @Test
    void filtroPorCampoAninhadoTrazSoAsOsDoMotorista() {
        // Given
        Motorista carlos = em.persist(Motorista.builder().nome("Carlos").cnh("CNH-1").build());
        Motorista dora = em.persist(Motorista.builder().nome("Dora").cnh("CNH-2").build());
        em.persist(OrdemServico.builder().dataServico(DIA).motorista(carlos).build());
        em.persist(OrdemServico.builder().dataServico(DIA).motorista(dora).build());
        em.persist(OrdemServico.builder().dataServico(DIA).build());
        em.flush();
        Specification<OrdemServico> doCarlos = Especificacoes.igual("motorista.id", carlos.getId());

        // When
        List<OrdemServico> encontradas = ordemServicoRepository.findAll(doCarlos);

        // Then
        assertThat(encontradas).singleElement()
                .satisfies(os -> assertThat(os.getMotorista().getId()).isEqualTo(carlos.getId()));
    }
}
