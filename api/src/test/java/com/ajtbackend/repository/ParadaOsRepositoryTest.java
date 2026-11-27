package com.ajtbackend.repository;

import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.ParadaOs;
import com.ajtbackend.model.Transfer;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: as consultas do ParadaOsRepository no banco real — o @EntityGraph traz os
 * transfers de cada parada na mesma query (sem N+1), a listagem paginada carrega os transfers
 * em lote (default_batch_fetch_size) e a busca pelas paradas das OS de um motorista (HU07).
 *
 * como rodar: @DataJpaTest com PostgreSQL via Testcontainers (RepositorioTestBase). conta as
 * queries reais com as estatisticas do Hibernate (generate_statistics no profile "test").
 * precisa de Docker; sem Docker a classe e pulada.
 *
 * por que existe: N+1 nao quebra nenhuma resposta, so deixa a tela lenta com volume — so
 * contando as queries de verdade da pra travar essa regressao.
 */
class ParadaOsRepositoryTest extends RepositorioTestBase {

    private static final int PARADAS = 5;
    private static final int TRANSFERS_POR_PARADA = 3;
    private static final LocalDate DIA = LocalDate.of(2031, 3, 10);

    @Autowired private TestEntityManager em;
    @Autowired private ParadaOsRepository repository;

    private OrdemServico osComParadas(Motorista motorista) {
        OrdemServico os = em.persist(OrdemServico.builder().dataServico(DIA).motorista(motorista).build());
        for (int p = 1; p <= PARADAS; p++) {
            Set<Transfer> transfers = new HashSet<>();
            for (int t = 0; t < TRANSFERS_POR_PARADA; t++) {
                transfers.add(em.persist(Transfer.builder().dataTransfer(DIA).horaTransfer(LocalTime.NOON)
                        .origem("Origem").destino("Destino").build()));
            }
            em.persist(ParadaOs.builder().ordemServico(os).ordemParada(p).localParada("Parada " + p)
                    .transfers(transfers).build());
        }
        return os;
    }

    // le os transfers de cada parada: e essa leitura que dispararia o N+1, entao faz parte da acao medida
    private static List<ParadaOs> comTransfersLidos(List<ParadaOs> paradas) {
        paradas.forEach(p -> p.getTransfers().size());
        return paradas;
    }

    // grava tudo e esvazia o cache da sessao, pra contagem medir idas reais ao banco
    private Statistics estatisticasDoBancoLimpo() {
        em.flush();
        em.clear();
        Statistics stats = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }

    @Test
    void deveTrazerAsParadasEmOrdemComOsTransfersNumaQuerySo_quandoListaAsParadasDaOs() {
        // given
        Long osId = osComParadas(null).getId();
        Statistics stats = estatisticasDoBancoLimpo();

        // when
        List<ParadaOs> paradas = comTransfersLidos(repository.findByOrdemServicoIdOrderByOrdemParadaAsc(osId));

        // then
        assertThat(paradas).extracting(ParadaOs::getOrdemParada).containsExactly(1, 2, 3, 4, 5);
        assertThat(paradas).extracting(p -> p.getTransfers().size()).containsOnly(TRANSFERS_POR_PARADA);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1); // sem o @EntityGraph: 1 + 5
    }

    @Test
    void deveCarregarOsTransfersEmLote_quandoListaAsParadasPaginado() {
        // given
        osComParadas(null);
        Statistics stats = estatisticasDoBancoLimpo();

        // when
        List<ParadaOs> paradas = comTransfersLidos(repository.findAll(PageRequest.of(0, 20, Sort.by("id"))).getContent());

        // then
        assertThat(paradas).hasSize(PARADAS);
        assertThat(paradas.stream().mapToInt(p -> p.getTransfers().size()).sum())
                .isEqualTo(PARADAS * TRANSFERS_POR_PARADA);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3); // pagina + count + 1 lote
    }

    @Test
    void deveTrazerSoAsParadasDasOsDele_quandoBuscaPorMotorista() {
        // given
        Motorista carlos = em.persist(Motorista.builder().nome("Carlos").cnh("CNH-CARLOS").build());
        Motorista dora = em.persist(Motorista.builder().nome("Dora").cnh("CNH-DORA").build());
        Long osDoCarlos = osComParadas(carlos).getId();
        osComParadas(dora);
        em.flush();

        // when
        Page<ParadaOs> doCarlos = repository.findByOrdemServicoMotoristaId(carlos.getId(), PageRequest.of(0, 20));

        // then
        assertThat(doCarlos.getTotalElements()).isEqualTo(PARADAS);
        assertThat(doCarlos.getContent()).allSatisfy(p -> assertThat(p.getOrdemServico().getId()).isEqualTo(osDoCarlos));
    }

    @Test
    void deveDevolverPaginaVazia_quandoMotoristaNaoTemOs() {
        // given
        Motorista semOs = em.persist(Motorista.builder().nome("Maria").cnh("CNH-MARIA").build());
        Motorista comOs = em.persist(Motorista.builder().nome("Pedro").cnh("CNH-PEDRO").build());
        osComParadas(comOs);
        em.flush();

        // when
        Page<ParadaOs> doSemOs = repository.findByOrdemServicoMotoristaId(semOs.getId(), PageRequest.of(0, 20));

        // then
        assertThat(doSemOs.getContent()).isEmpty();
    }
}
