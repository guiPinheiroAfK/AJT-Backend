package com.AJTBackend.integracao;

import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.AJTBackend.repository.TransferRepository;
import com.AJTBackend.service.ParadaOsService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: que o problema de N+1 corrigido no ParadaOsRepository (ver
 * @EntityGraph la) nao volta — listar as paradas de uma OS, e a listagem
 * paginada de paradas, nao podem virar "1 query por parada" de novo.
 *
 * como rodar: precisa de docker (herda de IntegracaoTestBase). usa as
 * estatisticas do hibernate (hibernate.generate_statistics=true no profile
 * "test", em application-test.yml) pra contar queries de verdade. roda com
 * "mvn test"; sem docker, a classe inteira e pulada.
 *
 * por que existe: N+1 e um bug de performance que nenhum teste funcional
 * comum detecta (a resposta continua correta, só fica lenta com volume) —
 * so contando as queries reais dá pra travar essa regressao no ci.
 */
class PerformanceIntegrationTest extends IntegracaoTestBase {

    private static final int PARADAS = 5;
    private static final int TRANSFERS_POR_PARADA = 3;

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ParadaOsService paradaOsService;
    @Autowired private OrdemServicoRepository ordemServicoRepository;
    @Autowired private ParadaOsRepository paradaOsRepository;
    @Autowired private TransferRepository transferRepository;

    private Long osId;

    @BeforeEach
    void criarMassa() {
        OrdemServico os = ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.now()).build());
        osId = os.getId();

        for (int p = 1; p <= PARADAS; p++) {
            Set<Transfer> transfers = new HashSet<>();
            for (int t = 0; t < TRANSFERS_POR_PARADA; t++) {
                transfers.add(transferRepository.save(Transfer.builder()
                        .dataTransfer(LocalDate.now()).horaTransfer(LocalTime.NOON)
                        .origem("Origem").destino("Destino").build()));
            }
            paradaOsRepository.save(ParadaOs.builder()
                    .ordemServico(os).ordemParada(p).localParada("Parada " + p).transfers(transfers).build());
        }
    }

    @Test
    void listarParadasDaOsNaoFazUmaQueryPorParada() {
        Statistics stats = estatisticasZeradas();

        var paradas = paradaOsService.listarPorOrdemServico(osId);

        assertThat(paradas).hasSize(PARADAS);
        assertThat(paradas).allSatisfy(p -> assertThat(p.transferIds()).hasSize(TRANSFERS_POR_PARADA));
        // antes: 1 (paradas) + 5 (transfers de cada parada) = 6 queries
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void listagemPaginadaDeParadasCarregaTransfersEmLote() {
        Statistics stats = estatisticasZeradas();

        var pagina = paradaOsService.listarTodos(PageRequest.of(0, 20));

        assertThat(pagina.conteudo()).isNotEmpty();
        // pagina + count + transfers em lote (default_batch_fetch_size), independente do tamanho da pagina
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    private Statistics estatisticasZeradas() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }
}
