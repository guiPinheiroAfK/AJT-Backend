package com.AJTBackend.web;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: validacao de entrada e formato de erro do GlobalExceptionHandler
 * — campo obrigatorio, tamanho maior que o do banco, enum invalido (lista os
 * valores aceitos), PATCH aceitando campo ausente mas recusando string vazia,
 * id com tipo errado na url, json malformado, rota inexistente e paginacao
 * (tamanho padrao e maximo).
 *
 * como rodar: @WebMvcTest com usuario simulado (with(user(...).roles(...))).
 * sem banco, sem docker. roda com "mvn test".
 *
 * por que existe: garante que toda resposta de erro segue o mesmo formato
 * (ErroResponseDTO) que o front espera, e que a validacao bate com as
 * colunas do banco (ver a V1-V6 nas migrations) — sem isso, um campo maior
 * que o permitido só falharia tarde, com um erro 500 feio do banco em vez
 * de um 400 claro.
 */
class ValidacaoWebTest extends WebTestBase {

    @Test
    void camposObrigatoriosRetornam400ComDetalhes() throws Exception {
        mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-20\",\"horaTransfer\":\"14:30:00\",\"origem\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Erro de validação"))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("destino"))));
        verify(transferService, never()).criar(any());
    }

    @Test
    void campoMaiorQueOBancoRetorna400() throws Exception {
        String origemGigante = "x".repeat(101);
        mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-20\",\"horaTransfer\":\"14:30:00\",\"origem\":\""
                                + origemGigante + "\",\"destino\":\"Hotel\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("100 caracteres"))));
    }

    @Test
    void enumInvalidoListaValoresAceitos() throws Exception {
        mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataTransfer\":\"2026-09-20\",\"horaTransfer\":\"14:30:00\",\"origem\":\"A\",\"destino\":\"B\",\"status\":\"XPTO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]", containsString("status")))
                .andExpect(jsonPath("$.detalhes[0]", containsString("AGUARDANDO_OS")));
    }

    @Test
    void patchAceitaCamposAusentesMasRecusaTextoVazio() throws Exception {
        mockMvc.perform(patch("/api/transfers/1").with(user("t").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMADO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/transfers/1").with(user("t").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origem\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))));
    }

    @Test
    void moedaInvalidaRetorna400() throws Exception {
        mockMvc.perform(patch("/api/transfers/1").with(user("t").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moedaOrigem\":\"DOLAR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idComTipoErradoRetorna400() throws Exception {
        mockMvc.perform(get("/api/transfers/abc").with(user("t").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("abc")));
        verify(transferService, never()).buscarPorId(anyLong());
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Requisição inválida"));
    }

    @Test
    void rotaInexistenteRetorna404EmJson() throws Exception {
        mockMvc.perform(get("/api/nao-existe").with(user("t").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void loginSemCamposRetorna400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes.length()").value(2));
    }

    @Test
    void cotacaoComCodigoDeMoedaInvalido() throws Exception {
        mockMvc.perform(get("/api/cotacao").param("de", "US").param("para", "BRL")
                        .with(user("t").roles("ATENDENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusDeBuscaInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/transfers/buscar").param("status", "XPTO")
                        .with(user("t").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginacaoTemPadraoETamanhoMaximo() throws Exception {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        mockMvc.perform(get("/api/transfers").with(user("t").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/transfers").param("size", "5000").param("page", "2")
                        .with(user("t").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(transferService, org.mockito.Mockito.times(2)).listarTodos(captor.capture());
        Pageable padrao = captor.getAllValues().get(0);
        Pageable grande = captor.getAllValues().get(1);

        assertThat(padrao.getPageSize()).isEqualTo(20);
        assertThat(padrao.getSort().getOrderFor("id")).isNotNull();
        assertThat(grande.getPageSize()).isEqualTo(100);
        assertThat(grande.getPageNumber()).isEqualTo(2);
        assertThat(grande.getSort()).isEqualTo(Sort.by("id"));
    }
}
