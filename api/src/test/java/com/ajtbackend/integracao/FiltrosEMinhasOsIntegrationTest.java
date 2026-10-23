package com.ajtbackend.integracao;

import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.ParadaOs;
import com.ajtbackend.model.Usuario;
import com.ajtbackend.model.enums.Role;
import com.ajtbackend.repository.MotoristaRepository;
import com.ajtbackend.repository.OrdemServicoRepository;
import com.ajtbackend.repository.ParadaOsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: os filtros das listagens (RF015) e a regra "o motorista so ve as
 * proprias OS" (HU07) contra um postgres real — as Specifications viram SQL de
 * verdade (like sem diferenciar maiusculas, periodo, relacao com a OS) e o
 * vinculo usuario -> motorista da V11 decide o que o MOTORISTA enxerga.
 *
 * como rodar: herda IntegracaoTestBase (testcontainers); precisa do docker.
 * cada teste usa datas e textos so dele (ano 2032 com um mes por teste, nomes
 * com UUID) pra nao misturar com os registros dos outros testes no mesmo banco.
 *
 * por que existe: filtro errado nao quebra nada, so devolve a lista errada —
 * e motorista vendo OS de outro motorista e vazamento que ninguem percebe.
 */
class FiltrosEMinhasOsIntegrationTest extends IntegracaoTestBase {

    @Autowired private MotoristaRepository motoristaRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;
    @Autowired private ParadaOsRepository paradaOsRepository;

    // cenario da HU07, montado por motoristaLigadoComOsDeHoje()
    private Motorista eu;
    private OrdemServico minha;
    private OrdemServico deOutro;
    private String tokenMotorista;

    private void criarTransfer(String data, String origem) throws Exception {
        criarPelaApi("/api/transfers", bearer(Role.ATENDENTE),
                "{\"dataTransfer\":\"" + data + "\",\"horaTransfer\":\"10:00:00\",\"origem\":\"" + origem
                        + "\",\"destino\":\"Hotel\"}");
    }

    private void tresTransfersNoMes(String anoMes, String origemDoDia2) throws Exception {
        criarTransfer(anoMes + "-01", "Rodoviaria");
        criarTransfer(anoMes + "-02", origemDoDia2);
        criarTransfer(anoMes + "-03", "Aeroporto");
    }

    private Motorista motorista(String nome) {
        return motoristaRepository.save(Motorista.builder()
                .nome(nome).cnh(UUID.randomUUID().toString().substring(0, 11)).build());
    }

    private ResultActions criarUsuarioMotorista(Long motoristaId) throws Exception {
        return postJson("/api/usuarios", bearer(Role.ADMIN),
                "{\"nome\":\"Eu\",\"username\":\"mot-" + UUID.randomUUID().toString().substring(0, 8)
                        + "\",\"senha\":\"senha12345\",\"role\":\"MOTORISTA\",\"motoristaId\":" + motoristaId + "}");
    }

    // duas OS de hoje (uma minha com parada, uma de outro motorista) e o usuario MOTORISTA ligado a mim
    private void motoristaLigadoComOsDeHoje() throws Exception {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        eu = motorista("Eu Motorista");
        Motorista outro = motorista("Outro Motorista");
        minha = ordemServicoRepository.save(OrdemServico.builder().dataServico(hoje).motorista(eu).build());
        deOutro = ordemServicoRepository.save(OrdemServico.builder().dataServico(hoje).motorista(outro).build());
        paradaOsRepository.save(ParadaOs.builder().ordemServico(minha).ordemParada(1).localParada("Aeroporto").build());
        ResultActions criado = criarUsuarioMotorista(eu.getId());
        criado.andExpect(status().isCreated());
        String username = corpoDe(criado).get("username").asText();
        tokenMotorista = "Bearer " + jwtService.gerarToken(username, Role.MOTORISTA);
    }

    // ---------------- RF015 ----------------

    @Test
    void deveTrazerSoOsDoPeriodo_quandoFiltraTransfersPorDataInicioEFim() throws Exception {
        // given
        tresTransfersNoMes("2032-03", "Centro");

        // when
        ResultActions resposta = executar(get("/api/transfers").param("dataInicio", "2032-03-02").param("dataFim", "2032-03-03"),
                bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].origem", containsInAnyOrder("Centro", "Aeroporto")));
    }

    @Test
    void deveAcharSemDiferenciarMaiusculas_quandoBuscaPorParteDaOrigem() throws Exception {
        // given
        String marca = "Marina-" + UUID.randomUUID().toString().substring(0, 6);
        tresTransfersNoMes("2032-05", marca);

        // when
        ResultActions resposta = executar(get("/api/transfers").param("busca", marca.toUpperCase().substring(0, 9))
                .param("dataInicio", "2032-05-01"), bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].origem").value(marca));
    }

    @Test
    void deveSomarOsFiltros_quandoPedeSemOsEStatusNoPeriodo() throws Exception {
        // given
        tresTransfersNoMes("2032-06", "Centro");

        // when
        ResultActions resposta = executar(get("/api/transfers").param("dataInicio", "2032-06-01").param("dataFim", "2032-06-03")
                .param("semOs", "true").param("status", "AGUARDANDO_OS"), bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.conteudo[*].osId", everyItem(nullValue())));
    }

    @Test
    void deveRetornar400_quandoPeriodoDoFiltroEstaInvertido() throws Exception {
        // when
        ResultActions resposta = executar(get("/api/transfers").param("dataInicio", "2032-03-05").param("dataFim", "2032-03-01"),
                bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("depois da final")));
    }

    @Test
    void deveAcharOPassageiro_quandoFiltraPorParteDoNomeEmMinusculo() throws Exception {
        // given
        String sobrenome = "Zq" + UUID.randomUUID().toString().substring(0, 6);
        criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE),
                "{\"nome\":\"Ana " + sobrenome + "\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X1\","
                        + "\"nacionalidade\":\"Argentina\"}");

        // when
        ResultActions resposta = executar(get("/api/passageiros").param("nome", sobrenome.toLowerCase()), bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nacionalidade").value("Argentina"));
    }

    @Test
    void deveAcharOMotorista_quandoFiltraPorParteDoNome() throws Exception {
        // given
        String sobrenome = "Zq" + UUID.randomUUID().toString().substring(0, 6);
        motorista("Carlos " + sobrenome);

        // when
        ResultActions resposta = executar(get("/api/motoristas").param("nome", sobrenome), bearer(Role.GERENTE));

        // then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Carlos " + sobrenome));
    }

    @Test
    void deveTrazerSoAsDoMotorista_quandoFiltraOsPorMotorista() throws Exception {
        // given
        Motorista carlos = motorista("Carlos");
        Motorista dora = motorista("Dora");
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 2)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(dora).build());

        // when
        ResultActions resposta = executar(get("/api/ordens-servico").param("motoristaId", String.valueOf(carlos.getId())),
                bearer(Role.GERENTE));

        // then
        resposta.andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void deveSomarMotoristaEPeriodo_quandoFiltraOsPelosDois() throws Exception {
        // given
        Motorista carlos = motorista("Carlos");
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 1)).motorista(carlos).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.of(2032, 4, 2)).motorista(carlos).build());

        // when
        ResultActions resposta = executar(get("/api/ordens-servico").param("motoristaId", String.valueOf(carlos.getId()))
                .param("dataInicio", "2032-04-02"), bearer(Role.GERENTE));

        // then
        resposta.andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].dataServico").value("2032-04-02"));
    }

    // ---------------- HU07 ----------------

    @Test
    void deveLigarOUsuarioAoCadastro_quandoAdminCriaUsuarioMotoristaComMotoristaId() throws Exception {
        // given
        Motorista carlos = motorista("Carlos");

        // when
        ResultActions resposta = criarUsuarioMotorista(carlos.getId());

        // then
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.motoristaId").value(carlos.getId()));
    }

    @Test
    void deveListarSoAsPropriasOs_quandoMotoristaListaAsOrdens() throws Exception {
        // given
        motoristaLigadoComOsDeHoje();

        // when
        ResultActions resposta = executar(get("/api/ordens-servico").param("size", "100"), tokenMotorista);

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].motoristaId", everyItem(is(eu.getId().intValue()))))
                .andExpect(jsonPath("$.conteudo[*].id", hasItem(minha.getId().intValue())))
                .andExpect(jsonPath("$.conteudo[*].id", not(hasItem(deOutro.getId().intValue()))));
    }

    @Test
    void deveRetornar404_quandoMotoristaBuscaOsDeOutro() throws Exception {
        // given
        motoristaLigadoComOsDeHoje();

        // when
        // a OS do outro motorista "nao existe" pra ele
        ResultActions resposta = getComo("/api/ordens-servico/" + deOutro.getId(), tokenMotorista);

        // then
        resposta.andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404_quandoMotoristaPedeOPdfDaOsDeOutro() throws Exception {
        // given
        motoristaLigadoComOsDeHoje();

        // when
        ResultActions resposta = getComo("/api/ordens-servico/" + deOutro.getId() + "/pdf", tokenMotorista);

        // then
        resposta.andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar404_quandoMotoristaListaAsParadasDaOsDeOutro() throws Exception {
        // given
        motoristaLigadoComOsDeHoje();

        // when
        ResultActions resposta = getComo("/api/paradas-os/ordem-servico/" + deOutro.getId(), tokenMotorista);

        // then
        resposta.andExpect(status().isNotFound());
    }

    @Test
    void deveTrazerAsOsDeHojeComParadas_quandoMotoristaPedeAsMinhas() throws Exception {
        // given
        motoristaLigadoComOsDeHoje();

        // when
        ResultActions resposta = getComo("/api/ordens-servico/minhas", tokenMotorista);

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ordem.id").value(minha.getId()))
                .andExpect(jsonPath("$[0].paradas", hasSize(1)))
                .andExpect(jsonPath("$[0].paradas[0].localParada").value("Aeroporto"));
    }

    @Test
    void deveVerAOsDeQualquerMotorista_quandoGerenteBusca() throws Exception {
        // given
        motoristaLigadoComOsDeHoje();

        // when
        ResultActions resposta = getComo("/api/ordens-servico/" + deOutro.getId(), bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deOutro.getId()));
    }

    @Test
    void deveRetornar403_quandoGerentePedeAsMinhas() throws Exception {
        // when
        // "minhas" e so do motorista
        ResultActions resposta = getComo("/api/ordens-servico/minhas", bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void naoDeveVerNenhumaOs_quandoMotoristaSemCadastroLigadoListaAsOrdens() throws Exception {
        // given
        Usuario semVinculo = criarUsuario(Role.MOTORISTA, "senha-teste-123");
        String token = "Bearer " + jwtService.gerarToken(semVinculo.getUsername(), Role.MOTORISTA);

        // when
        ResultActions resposta = getComo("/api/ordens-servico", token);

        // then
        resposta.andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void deveDevolverListaVazia_quandoMotoristaSemCadastroLigadoPedeAsMinhas() throws Exception {
        // given
        Usuario semVinculo = criarUsuario(Role.MOTORISTA, "senha-teste-123");
        String token = "Bearer " + jwtService.gerarToken(semVinculo.getUsername(), Role.MOTORISTA);

        // when
        ResultActions resposta = getComo("/api/ordens-servico/minhas", token);

        // then
        resposta.andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deveRetornar400_quandoLigaMotoristaAUsuarioDeOutroPerfil() throws Exception {
        // given
        Motorista carlos = motorista("Carlos");

        // when
        ResultActions resposta = postJson("/api/usuarios", bearer(Role.ADMIN),
                "{\"nome\":\"Ana\",\"username\":\"ana-" + UUID.randomUUID().toString().substring(0, 8)
                        + "\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\",\"motoristaId\":" + carlos.getId() + "}");

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("perfil Motorista")));
    }
}
