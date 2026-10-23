package com.AJTBackend.integracao;

import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.VeiculoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 */
class PersistenciaIntegrationTest extends IntegracaoTestBase {

    // documento proprio por teste: o banco e compartilhado e documento repetido e recusado (V12)
    private static String passageiro(String documento) {
        return "{\"nome\":\"John Doe\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"" + documento
                + "\",\"nacionalidade\":\"Americana\"}";
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MotoristaRepository motoristaRepository;
    @Autowired private VeiculoRepository veiculoRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;

    private ResultActions cadastrarPassageiro(String documento) throws Exception {
        return mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON).content(passageiro(documento)));
    }

    private long idDoPassageiroCadastrado(String documento) throws Exception {
        String json = cadastrarPassageiro(documento).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void documentoDoPassageiroFicaCifradoNoBanco() throws Exception {
        // When
        ResultActions resposta = cadastrarPassageiro("XP9988776");

        // Then
        long id = objectMapper.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asLong();
        String noBanco = jdbcTemplate.queryForObject("SELECT documento FROM passageiros WHERE id = ?", String.class, id);
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento").value("XP9988776"));
        assertThat(noBanco).startsWith("v1:").doesNotContain("XP9988776");
    }

    @Test
    void mesmoCpfComOutraPontuacaoERecusadoEOHashNaoRevelaONumero() throws Exception {
        // Given: um CPF gravado sem pontuacao
        String primeiro = "{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\",\"documento\":\"10000000108\"}";
        String json = mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(primeiro))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();

        // When: outro passageiro com o mesmo CPF, agora com pontuacao
        ResultActions resposta = mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Bia\",\"tipoDocumento\":\"CPF\",\"documento\":\"100.000.001-08\"}"));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Já existe passageiro cadastrado com este CPF."));
        String hash = jdbcTemplate.queryForObject("SELECT documento_hash FROM passageiros WHERE id = ?", String.class, id);
        assertThat(hash).hasSize(64).doesNotContain("10000000108");
    }

    @Test
    void documentoDoPassageiroVoltaLegivelNaApiParaOAtendente() throws Exception {
        // Given
        long id = idDoPassageiroCadastrado("XP1122334");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/passageiros/" + id).header("Authorization", bearer(Role.ATENDENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("XP1122334"));
    }

    @Test
    void motoristaNaoLeOCadastroDePassageiros() throws Exception {
        // Given: LGPD, o motorista so ve os passageiros dos transfers da OS dele, com documento mascarado
        long id = idDoPassageiroCadastrado("XP5566778");

        // When
        ResultActions resposta = mockMvc.perform(get("/api/passageiros/" + id).header("Authorization", bearer(Role.MOTORISTA)));

        // Then
        resposta.andExpect(status().isForbidden());
    }

    @Test
    void excluirMotoristaEmUsoPorOrdemDeServicoRetorna409() throws Exception {
        // Given
        Motorista motorista = motoristaRepository.save(Motorista.builder()
                .nome("Carlos").cnh(UUID.randomUUID().toString().substring(0, 11)).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.now()).motorista(motorista).build());

        // When
        ResultActions resposta = mockMvc.perform(delete("/api/motoristas/" + motorista.getId())
                .header("Authorization", bearer(Role.GERENTE)));

        // Then
        resposta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de dados"));
        assertThat(motoristaRepository.existsById(motorista.getId())).isTrue();
    }

    @Test
    void paginacaoRealDevolveOsTotais() throws Exception {
        // Given
        for (int i = 0; i < 3; i++) {
            veiculoRepository.save(Veiculo.builder().label("Van " + i)
                    .placa(UUID.randomUUID().toString().substring(0, 7)).capacidade(15).build());
        }

        // When
        ResultActions resposta = mockMvc.perform(get("/api/veiculos").param("size", "2").param("sort", "id,desc")
                .header("Authorization", bearer(Role.ATENDENTE)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.primeira").value(true));
    }

    @Test
    void ordenarPorCampoInexistenteRetorna400() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/veiculos").param("sort", "campoQueNaoExiste")
                .header("Authorization", bearer(Role.ADMIN)));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("campoQueNaoExiste")));
    }

    @Test
    void enumDeStatusEhPersistidoEFiltrado() throws Exception {
        // Given
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"status\":\"CONFIRMADO\",\"valorBase\":300.00,\"moedaOrigem\":\"BRL\"}"))
                .andExpect(status().isCreated());

        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers/buscar").param("status", "CONFIRMADO")
                .header("Authorization", bearer(Role.MOTORISTA)));

        // Then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].status", everyItem(equalTo("CONFIRMADO"))))
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(1)));
    }

    @Test
    void transferApontandoParaOsInexistenteRetorna404() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"osId\":999999}"));

        // Then
        resposta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", containsString("999999")));
    }
}
