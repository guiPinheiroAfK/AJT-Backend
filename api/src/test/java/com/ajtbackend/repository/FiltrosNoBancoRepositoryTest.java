package com.ajtbackend.repository;

import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.Transfer;
import com.ajtbackend.model.enums.StatusTransfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.mapping.PropertyReferenceException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/*
 * o que testa: as Especificacoes (filtros das listagens, RF015) executadas no banco real —
 * "contem" sem diferenciar maiusculas, % e _ digitados tratados como texto (escape do LIKE),
 * busca em mais de um campo, periodo fechado ou aberto, filtros somados, ordenacao, filtro
 * por campo aninhado e o filtro "nenhum".
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
        transfer(data, origem, destino, StatusTransfer.AGUARDANDO_OS);
    }

    private void transfer(LocalDate data, String origem, String destino, StatusTransfer status) {
        em.persist(Transfer.builder().dataTransfer(data).horaTransfer(LocalTime.NOON)
                .origem(origem).destino(destino).status(status).build());
    }

    private void tresOrigensComPorcento() {
        transfer(DIA, "Promo 100% Aeroporto", "Hotel");
        transfer(DIA, "Aeroporto 100 Norte", "Hotel");
        transfer(DIA, "Rodoviaria", "Hotel");
        em.flush();
    }

    private static List<String> origens(List<Transfer> transfers) {
        return transfers.stream().map(Transfer::getOrigem).sorted().toList();
    }

    // ----- texto -----

    @Test
    void deveTratarPorcentoComoTexto_quandoBuscaTemNumeroEPorcento() {
        // given
        tresOrigensComPorcento();

        // when
        List<Transfer> comPorcentagem = transferRepository.findAll(Especificacoes.contem("origem", "100%"));

        // then
        assertThat(origens(comPorcentagem)).containsExactly("Promo 100% Aeroporto");
    }

    @Test
    void deveAcharSoQuemTemPorcento_quandoBuscaEhSoOPorcento() {
        // given
        tresOrigensComPorcento();

        // when
        List<Transfer> soPorcentagem = transferRepository.findAll(Especificacoes.contem("origem", "%"));

        // then
        assertThat(origens(soPorcentagem)).containsExactly("Promo 100% Aeroporto");
    }

    @Test
    void deveIgnorarMaiusculas_quandoBuscaVemEmMaiusculo() {
        // given
        tresOrigensComPorcento();

        // when
        List<Transfer> aeroporto = transferRepository.findAll(Especificacoes.contem("origem", "AEROPORTO"));

        // then
        assertThat(origens(aeroporto)).containsExactly("Aeroporto 100 Norte", "Promo 100% Aeroporto");
    }

    @Test
    void naoDeveVirarCuringaDeUmCaractere_quandoBuscaTemUnderline() {
        // given
        transfer(DIA, "Portao_3", "Hotel");
        transfer(DIA, "PortaoX3", "Hotel");
        em.flush();

        // when
        List<Transfer> encontrados = transferRepository.findAll(Especificacoes.contem("origem", "portao_3"));

        // then
        assertThat(origens(encontrados)).containsExactly("Portao_3");
    }

    @Test
    void deveProcurarNaOrigemOuNoDestino_quandoAlgumContemRecebeOsDoisCampos() {
        // given
        transfer(DIA, "GRU", "Hotel Centro");
        transfer(DIA, "Centro de Convencoes", "GRU");
        transfer(DIA, "Rodoviaria", "Praia");
        em.flush();

        // when
        List<Transfer> encontrados = transferRepository.findAll(Especificacoes.algumContem("centro", "origem", "destino"));

        // then
        assertThat(origens(encontrados)).containsExactly("Centro de Convencoes", "GRU");
    }

    // ----- periodo e filtros somados -----

    @Test
    void deveIncluirAsDuasPontasEDeixarDeForaOResto_quandoPeriodoEhFechado() {
        // given
        transfer(DIA.minusDays(1), "Antes", "X");
        transfer(DIA, "Inicio", "X");
        transfer(DIA.plusDays(5), "Fim", "X");
        transfer(DIA.plusDays(6), "Depois", "X");
        em.flush();

        // when
        List<Transfer> noPeriodo = transferRepository.findAll(Especificacoes.dataEntre("dataTransfer", DIA, DIA.plusDays(5)));

        // then
        assertThat(origens(noPeriodo)).containsExactly("Fim", "Inicio");
    }

    @Test
    void deveTrazerAteODiaInclusive_quandoPeriodoTemSoOFim() {
        // given
        transfer(DIA.minusDays(10), "Antes", "X");
        transfer(DIA, "No dia", "X");
        transfer(DIA.plusDays(10), "Depois", "X");
        em.flush();

        // when
        List<Transfer> ateODia = transferRepository.findAll(Especificacoes.dataEntre("dataTransfer", null, DIA));

        // then
        assertThat(origens(ateODia)).containsExactly("Antes", "No dia");
    }

    @Test
    void deveSomarStatusPeriodoEBusca_quandoOsTresFiltrosSaoInformados() {
        // given
        transfer(DIA, "Aeroporto Norte", "Hotel", StatusTransfer.CONFIRMADO);
        transfer(DIA, "Aeroporto Norte", "Hotel", StatusTransfer.CANCELADO);
        transfer(DIA, "Rodoviaria", "Hotel", StatusTransfer.CONFIRMADO);
        transfer(DIA.plusDays(30), "Aeroporto Norte", "Hotel", StatusTransfer.CONFIRMADO);
        em.flush();
        Specification<Transfer> filtros = Specification.allOf(
                Especificacoes.igual("status", StatusTransfer.CONFIRMADO),
                Especificacoes.dataEntre("dataTransfer", DIA, DIA.plusDays(7)),
                Especificacoes.algumContem("aeroporto", "origem", "destino"));

        // when
        List<Transfer> achados = transferRepository.findAll(filtros);

        // then
        assertThat(achados).singleElement().satisfies(t -> {
            assertThat(t.getOrigem()).isEqualTo("Aeroporto Norte");
            assertThat(t.getStatus()).isEqualTo(StatusTransfer.CONFIRMADO);
            assertThat(t.getDataTransfer()).isEqualTo(DIA);
        });
    }

    // ----- ordenacao -----

    @Test
    void deveOrdenarPelaDataDecrescente_quandoPaginaPedeOrdenacao() {
        // given
        transfer(DIA, "Primeiro", "X");
        transfer(DIA.plusDays(2), "Terceiro", "X");
        transfer(DIA.plusDays(1), "Segundo", "X");
        em.flush();
        PageRequest pagina = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dataTransfer"));

        // when
        Page<Transfer> resultado = transferRepository.findAll(Especificacoes.dataEntre("dataTransfer", DIA, DIA.plusDays(2)), pagina);

        // then
        assertThat(resultado.getContent())
                .extracting(Transfer::getOrigem)
                .containsExactly("Terceiro", "Segundo", "Primeiro");
    }

    @Test
    void deveLancarPropertyReference_quandoOrdenaPorCampoInexistente() {
        // given
        transfer(DIA, "Aeroporto", "X");
        em.flush();
        PageRequest pagina = PageRequest.of(0, 10, Sort.by("campoQueNaoExiste"));

        // when
        Throwable erro = catchThrowable(() -> transferRepository.findAll(Especificacoes.dataEntre("dataTransfer", DIA, DIA), pagina));

        // then
        assertThat(erro).isInstanceOf(PropertyReferenceException.class);
    }

    // ----- ordens de servico -----

    @Test
    void deveTrazerSoAsOsDoMotorista_quandoFiltraPorCampoAninhado() {
        // given
        Motorista carlos = em.persist(Motorista.builder().nome("Carlos").cnh("CNH-1").build());
        Motorista dora = em.persist(Motorista.builder().nome("Dora").cnh("CNH-2").build());
        em.persist(OrdemServico.builder().dataServico(DIA).motorista(carlos).build());
        em.persist(OrdemServico.builder().dataServico(DIA).motorista(dora).build());
        em.persist(OrdemServico.builder().dataServico(DIA).build());
        em.flush();
        Specification<OrdemServico> doCarlos = Especificacoes.igual("motorista.id", carlos.getId());

        // when
        List<OrdemServico> encontradas = ordemServicoRepository.findAll(doCarlos);

        // then
        assertThat(encontradas).singleElement()
                .satisfies(os -> assertThat(os.getMotorista().getId()).isEqualTo(carlos.getId()));
    }

    @Test
    void deveTrazerSoAsOsDoMotoristaNoDia_quandoFiltraPorMotoristaEData() {
        // given
        // a mesma consulta das "minhas OS do dia" (HU07)
        Motorista carlos = em.persist(Motorista.builder().nome("Carlos").cnh("CNH-1").build());
        Motorista dora = em.persist(Motorista.builder().nome("Dora").cnh("CNH-2").build());
        OrdemServico doCarlosNoDia = em.persist(OrdemServico.builder().dataServico(DIA).motorista(carlos).build());
        em.persist(OrdemServico.builder().dataServico(DIA.plusDays(1)).motorista(carlos).build());
        em.persist(OrdemServico.builder().dataServico(DIA).motorista(dora).build());
        em.flush();
        Specification<OrdemServico> minhasDoDia = Specification.allOf(
                Especificacoes.igual("motorista.id", carlos.getId()),
                Especificacoes.igual("dataServico", DIA));

        // when
        List<OrdemServico> encontradas = ordemServicoRepository.findAll(minhasDoDia, Sort.by("id"));

        // then
        assertThat(encontradas).extracting(OrdemServico::getId).containsExactly(doCarlosNoDia.getId());
    }

    @Test
    void naoDeveTrazerNenhumaOs_quandoFiltroEhNenhum() {
        // given
        // usado quando o usuario MOTORISTA nao tem cadastro de motorista ligado
        Motorista carlos = em.persist(Motorista.builder().nome("Carlos").cnh("CNH-1").build());
        em.persist(OrdemServico.builder().dataServico(DIA).motorista(carlos).build());
        em.persist(OrdemServico.builder().dataServico(DIA).build());
        em.flush();

        // when
        List<OrdemServico> encontradas = ordemServicoRepository.findAll(Especificacoes.<OrdemServico>nenhum());

        // then
        assertThat(encontradas).isEmpty();
    }
}
