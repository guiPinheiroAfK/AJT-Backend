package com.AJTBackend.integracao;

import com.AJTBackend.model.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: o RNF008 nos cadastros (passageiro, veiculo, usuario...): a resposta
 * traz a versao, a edicao com a versao certa passa e sobe a versao, e a edicao com
 * uma versao velha (outra pessoa salvou antes) volta 409 sem sobrescrever. tambem
 * confere que o login nao conta como edicao do usuario (ultimoLogin fora do lock).
 *
 * como rodar: herda IntegracaoTestBase (testcontainers); precisa do docker.
 *
 * por que existe: transfer e OS ja tinham esse teste; os cadastros ganharam a
 * mesma regra depois, e o jeito de quebrar ela sem perceber e esquecer o flush
 * (a resposta voltaria com a versao antiga e a proxima edicao daria 409 a toa).
 */
class VersaoCadastrosIntegrationTest extends IntegracaoTestBase {

    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String PASSAGEIRO_ANA = "{\"nome\":\"Ana\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"V1\"}";

    @Test
    void deveNascerComVersaoZero_quandoPassageiroEhCriado() throws Exception {
        // when
        ResultActions resposta = postJson("/api/passageiros", bearer(Role.ATENDENTE), PASSAGEIRO_ANA);

        // then
        resposta.andExpect(status().isCreated())
                .andExpect(jsonPath("$.versao").value(0));
    }

    @Test
    void deveSalvarESubirAVersao_quandoPatchDePassageiroTrazAVersaoAtual() throws Exception {
        // given
        long id = criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE), PASSAGEIRO_ANA);

        // when
        ResultActions resposta = patchJson("/api/passageiros/" + id, bearer(Role.ATENDENTE), "{\"nome\":\"Ana Maria\",\"versao\":0}");

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void deveRetornar409_quandoPatchDePassageiroTrazVersaoQueJaFoiSalva() throws Exception {
        // given
        long id = criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE), PASSAGEIRO_ANA);
        patchJson("/api/passageiros/" + id, bearer(Role.ATENDENTE), "{\"nome\":\"Ana Maria\",\"versao\":0}")
                .andExpect(status().isOk());

        // when
        ResultActions resposta = patchJson("/api/passageiros/" + id, bearer(Role.GERENTE), "{\"nome\":\"Ana Paula\",\"versao\":0}");

        // then
        resposta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de edição"));
        assertThat(jdbcTemplate.queryForObject("SELECT nome FROM passageiros WHERE id = ?", String.class, id))
                .isEqualTo("Ana Maria");
    }

    @Test
    void devePreservarQuemSalvouAntes_quandoAEdicaoSeguinteLevou409() throws Exception {
        // given
        long id = criarPelaApi("/api/passageiros", bearer(Role.ATENDENTE), PASSAGEIRO_ANA);
        patchJson("/api/passageiros/" + id, bearer(Role.ATENDENTE), "{\"nome\":\"Ana Maria\",\"versao\":0}")
                .andExpect(status().isOk());
        patchJson("/api/passageiros/" + id, bearer(Role.GERENTE), "{\"nome\":\"Ana Paula\",\"versao\":0}")
                .andExpect(status().isConflict());

        // when
        ResultActions resposta = getComo("/api/passageiros/" + id, bearer(Role.ATENDENTE));

        // then
        resposta.andExpect(jsonPath("$.nome").value("Ana Maria"));
    }

    @Test
    void deveSalvarSemConferir_quandoClienteAntigoNaoMandaAVersao() throws Exception {
        // given
        String placa = ("V" + UUID.randomUUID().toString().replace("-", "")).substring(0, 7).toUpperCase();
        long id = criarPelaApi("/api/veiculos", bearer(Role.GERENTE),
                "{\"label\":\"Van\",\"placa\":\"" + placa + "\",\"capacidade\":10}");

        // when
        // sem "versao" no corpo: nao confere (cliente antigo), so grava e devolve a versao nova
        ResultActions resposta = patchJson("/api/veiculos/" + id, bearer(Role.GERENTE), "{\"capacidade\":12}");

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidade").value(12))
                .andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void naoDeveContarComoEdicao_quandoOUsuarioFazLogin() throws Exception {
        // given
        String username = "ver-" + UUID.randomUUID().toString().substring(0, 8);
        ResultActions criado = postJson("/api/usuarios", bearer(Role.ADMIN),
                "{\"nome\":\"Teste\",\"username\":\"" + username + "\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\"}");
        criado.andExpect(status().isCreated());
        JsonNode usuario = corpoDe(criado);
        long versao = usuario.get("versao").asLong();
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isOk());

        // when
        // o admin que abriu o cadastro antes do login ainda consegue salvar
        ResultActions resposta = patchJson("/api/usuarios/" + usuario.get("id").asLong(), bearer(Role.ADMIN),
                "{\"nome\":\"Teste 2\",\"versao\":" + versao + "}");

        // then
        resposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(versao + 1));
    }
}
