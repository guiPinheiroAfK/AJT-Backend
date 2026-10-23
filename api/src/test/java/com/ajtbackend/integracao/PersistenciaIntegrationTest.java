package com.ajtbackend.integracao;

import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.Veiculo;
import com.ajtbackend.model.enums.Role;
import com.ajtbackend.repository.MotoristaRepository;
import com.ajtbackend.repository.OrdemServicoRepository;
import com.ajtbackend.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: comportamento que so aparece com um postgres de verdade —
 * o documento do passageiro fica cifrado no banco (lido direto via jdbc)
 * mas volta legivel pela api; excluir um motorista em uso por uma OS da
 * 409 (constraint de FK); a paginacao real com totais e ordenacao; sort
 * por campo inexistente vira 400; e o enum de status e persistido e
 * filtrado certo.
 *
 * como rodar: precisa de docker (herda de IntegracaoTestBase). roda com
 * "mvn test"; sem docker, a classe inteira e pulada.
 *
 * por que existe: sao coisas que nenhum mock reproduz de verdade — a
 * criptografia do CriptografiaConverter so e exercitada pelo hibernate
 * numa gravacao/leitura real, e a violacao de FK so o postgres detecta.
 * aqui fica o caminho pela api (status http, json); o mesmo comportamento
 * so de banco tem o seu teste em repository/ com @DataJpaTest.
 */
class PersistenciaIntegrationTest extends IntegracaoTestBase {

    // documento proprio por teste: o banco e compartilhado e documento repetido e recusado (V12)
    private static String passageiroJohn(String documento) {
        return "{\"nome\":\"John Doe\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"" + documento
                + "\",\"nacionalidade\":\"Americana\"}";
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MotoristaRepository motoristaRepository;
    @Autowired private VeiculoRepository veiculoRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;

    @Test
    void deveDevolverEmClaroEGravarCifrado_quandoAtendenteCadastraPassageiro() throws Exception {
        // when
        ResultActions resposta = postJson("/api/passageiros", bearer(Role.ATENDENTE), passageiroJohn("XP9988776"));

        // then
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento").value("XP9988776"));
        String noBanco = jdbcTemplate.queryForObject("SELECT documento FROM passageiros WHERE id = ?", String.class, idDe(resposta));
        assertThat(noBanco).startsWith("v1:").doesNotContain("XP9988776");
    }

    @Test
    void deveRecusarComMensagemSemONumero_quandoOMesmoCpfVemComOutraPontuacao() throws Exception {
        // given: um CPF gravado sem pontuacao
        long id = criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE),
                "{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\",\"documento\":\"10000000108\"}");

        // when: outro passageiro com o mesmo CPF, agora com pontuacao
        ResultActions resposta = postJson("/api/passageiros", bearer(Role.ATENDENTE),
                "{\"nome\":\"Bia\",\"tipoDocumento\":\"CPF\",\"documento\":\"100.000.001-08\"}");

        // then: 400, e o hash gravado nao revela o numero
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Já existe passageiro cadastrado com este CPF."));
        String hash = jdbcTemplate.queryForObject("SELECT documento_hash FROM passageiros WHERE id = ?", String.class, id);
        assertThat(hash).hasSize(64).doesNotContain("10000000108");
    }

    @Test
    void deveDevolverODocumentoEmClaro_quandoAtendenteBuscaPassageiro() throws Exception {
        // given
        long id = criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE), passageiroJohn("XP1122334"));

        // when
        ResultActions resposta = getComo("/api/passageiros/" + id, bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("XP1122334"));
    }

    @Test
    void deveRetornar403_quandoMotoristaBuscaPassageiro() throws Exception {
        // given
        long id = criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE), passageiroJohn("XP5566778"));

        // when
        // motorista nao le o cadastro de passageiros (LGPD)
        ResultActions resposta = getComo("/api/passageiros/" + id, bearer(Role.MOTORISTA));

        // then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void deveRetornar409_quandoExcluiMotoristaEmUsoPorOrdemDeServico() throws Exception {
        // given
        Motorista motorista = motoristaRepository.save(Motorista.builder()
                .nome("Carlos").cnh(UUID.randomUUID().toString().substring(0, 11)).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.now()).motorista(motorista).build());

        // when
        ResultActions resposta = deleteComo("/api/motoristas/" + motorista.getId(), bearer(Role.GERENTE));

        // then
        resposta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de dados"));
        assertThat(motoristaRepository.existsById(motorista.getId())).isTrue();
    }

    @Test
    void deveDevolverPaginaComTotais_quandoListaVeiculosPaginado() throws Exception {
        // given
        for (int i = 0; i < 3; i++) {
            veiculoRepository.save(Veiculo.builder().label("Van " + i)
                    .placa(UUID.randomUUID().toString().substring(0, 7)).capacidade(15).build());
        }

        // when
        ResultActions resposta = executar(get("/api/veiculos").param("size", "2").param("sort", "id,desc"), bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.primeira").value(true));
    }

    @Test
    void deveRetornar400_quandoOrdenaPorCampoInexistente() throws Exception {
        // when
        ResultActions resposta = executar(get("/api/veiculos").param("sort", "campoQueNaoExiste"), bearer(Role.ADMIN));

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("campoQueNaoExiste")));
    }

    @Test
    void deveFiltrarPeloStatusGravado_quandoBuscaTransfersPorStatus() throws Exception {
        // given
        criarPelaApi("/api/transfers", bearer(Role.ATENDENTE),
                "{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"status\":\"CONFIRMADO\",\"valorBase\":300.00,\"moedaOrigem\":\"BRL\"}");

        // when
        ResultActions resposta = executar(get("/api/transfers/buscar").param("status", "CONFIRMADO"), bearer(Role.MOTORISTA));

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.conteudo[*].status", everyItem(equalTo("CONFIRMADO"))))
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(1)));
    }

    @Test
    void deveRetornar404_quandoTransferApontaParaOsInexistente() throws Exception {
        // when
        ResultActions resposta = postJson("/api/transfers", bearer(Role.ATENDENTE),
                "{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"osId\":999999}");

        // then
        resposta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", containsString("999999")));
    }
}
