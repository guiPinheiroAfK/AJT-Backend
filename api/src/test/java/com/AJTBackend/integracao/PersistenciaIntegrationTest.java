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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MotoristaRepository motoristaRepository;
    @Autowired private VeiculoRepository veiculoRepository;
    @Autowired private OrdemServicoRepository ordemServicoRepository;

    @Test
    void documentoDoPassageiroFicaCifradoNoBancoMasVoltaLegivelNaApi() throws Exception {
        String json = mockMvc.perform(post("/api/passageiros").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"John Doe\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"XP9988776\",\"nacionalidade\":\"Americana\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento").value("XP9988776"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();

        String noBanco = jdbcTemplate.queryForObject("SELECT documento FROM passageiros WHERE id = ?", String.class, id);
        assertThat(noBanco).startsWith("v1:").doesNotContain("XP9988776");

        mockMvc.perform(get("/api/passageiros/" + id).header("Authorization", bearer(Role.MOTORISTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("XP9988776"));
    }

    @Test
    void excluirMotoristaEmUsoPorOrdemDeServicoRetorna409() throws Exception {
        Motorista motorista = motoristaRepository.save(Motorista.builder()
                .nome("Carlos").cnh(UUID.randomUUID().toString().substring(0, 11)).build());
        ordemServicoRepository.save(OrdemServico.builder().dataServico(LocalDate.now()).motorista(motorista).build());

        mockMvc.perform(delete("/api/motoristas/" + motorista.getId()).header("Authorization", bearer(Role.GERENTE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de dados"));
    }

    @Test
    void paginacaoRealComTotais() throws Exception {
        for (int i = 0; i < 3; i++) {
            veiculoRepository.save(Veiculo.builder().label("Van " + i)
                    .placa(UUID.randomUUID().toString().substring(0, 7)).capacidade(15).build());
        }

        mockMvc.perform(get("/api/veiculos").param("size", "2").param("sort", "id,desc")
                        .header("Authorization", bearer(Role.ATENDENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.primeira").value(true));
    }

    @Test
    void ordenarPorCampoInexistenteRetorna400() throws Exception {
        mockMvc.perform(get("/api/veiculos").param("sort", "campoQueNaoExiste")
                        .header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enumDeStatusEhPersistidoEFiltrado() throws Exception {
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"status\":\"CONFIRMADO\",\"valorBase\":300.00,\"moedaOrigem\":\"BRL\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transfers/buscar").param("status", "CONFIRMADO")
                        .header("Authorization", bearer(Role.MOTORISTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].status").value("CONFIRMADO"));
    }

    @Test
    void transferApontandoParaOsInexistenteRetorna404() throws Exception {
        mockMvc.perform(post("/api/transfers").header("Authorization", bearer(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-21\",\"horaTransfer\":\"15:00:00\",\"origem\":\"GRU\",\"destino\":\"Hotel\",\"osId\":999999}"))
                .andExpect(status().isNotFound());
    }
}
