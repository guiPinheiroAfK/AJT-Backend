package com.AJTBackend.web;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidacaoWebTest extends WebTestBase {

    private static final String DATA_E_HORA = "\"dataTransfer\":\"2026-09-20\",\"horaTransfer\":\"14:30:00\"";

    private static String corpoTransfer(String camposExtras) {
        return "{" + DATA_E_HORA + "," + camposExtras + "}";
    }

    // ----- criacao de transfer: campos invalidos -----

    @Test
    void camposObrigatoriosRetornam400ComDetalhes() throws Exception {
        // Given: origem vazia e destino ausente
        String corpo = corpoTransfer("\"origem\":\"\"");

        // When
        ResultActions resposta = mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Erro de validação"))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("destino"))));
        verify(transferService, never()).criar(any());
    }

    @Test
    void campoMaiorQueOBancoRetorna400SemChamarOService() throws Exception {
        // Given
        String origemGigante = "x".repeat(101);
        String corpo = corpoTransfer("\"origem\":\"" + origemGigante + "\",\"destino\":\"Hotel\"");

        // When
        ResultActions resposta = mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("100 caracteres"))));
        verify(transferService, never()).criar(any());
    }

    @Test
    void enumInvalidoListaValoresAceitos() throws Exception {
        // Given
        String corpo = corpoTransfer("\"origem\":\"A\",\"destino\":\"B\",\"status\":\"XPTO\"");

        // When
        ResultActions resposta = mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes[0]", containsString("status")))
                .andExpect(jsonPath("$.detalhes[0]", containsString("AGUARDANDO_OS")));
        verify(transferService, never()).criar(any());
    }

    @Test
    void jsonMalformadoRetorna400SemChamarOService() throws Exception {
        // Given
        String corpoInvalido = "{nao e json";

        // When
        ResultActions resposta = mockMvc.perform(post("/api/transfers").with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoInvalido));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Requisição inválida"));
        verify(transferService, never()).criar(any());
    }

    // ----- PATCH -----

    @Test
    void patchAceitaCamposAusentes() throws Exception {
        // Given: so o status, sem nenhum outro campo
        String corpo = "{\"status\":\"CONFIRMADO\"}";

        // When
        ResultActions resposta = mockMvc.perform(patch("/api/transfers/1").with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        // Then
        resposta.andExpect(status().isOk());
        verify(transferService).atualizarParcial(eq(1L), any());
    }

    @Test
    void patchRecusaTextoVazioComEspacos() throws Exception {
        // Given
        String corpo = "{\"origem\":\"   \"}";

        // When
        ResultActions resposta = mockMvc.perform(patch("/api/transfers/1").with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))));
        verify(transferService, never()).atualizarParcial(anyLong(), any());
    }

    @Test
    void moedaInvalidaRetorna400SemChamarOService() throws Exception {
        // Given
        String corpo = "{\"moedaOrigem\":\"DOLAR\"}";

        // When
        ResultActions resposta = mockMvc.perform(patch("/api/transfers/1").with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("moedaOrigem"))));
        verify(transferService, never()).atualizarParcial(anyLong(), any());
    }

    // ----- parametros da url -----

    @Test
    void idComTipoErradoRetorna400SemChamarOService() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers/abc").with(user("t").roles("ADMIN")));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem", containsString("abc")));
        verify(transferService, never()).buscarPorId(anyLong());
    }

    @Test
    void rotaInexistenteRetorna404EmJson() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/nao-existe").with(user("t").roles("ADMIN")));

        // Then
        resposta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void cotacaoComCodigoDeMoedaInvalidoRetorna400() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/cotacao").param("de", "US").param("para", "BRL")
                .with(user("t").roles("ATENDENTE")));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(cotacaoService);
    }

    @Test
    void statusDeBuscaInvalidoRetorna400() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers/buscar").param("status", "XPTO")
                .with(user("t").roles("ADMIN")));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(transferService);
    }

    // ----- login -----

    @Test
    void loginSemCamposRetorna400ComUmDetalhePorCampo() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // Then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes.length()").value(2));
        verifyNoInteractions(authService);
    }

    // ----- paginacao -----

    @Test
    void paginacaoSemParametrosUsaTamanhoPadraoOrdenadoPorId() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers").with(user("t").roles("ADMIN")));

        // Then
        resposta.andExpect(status().isOk());
        Pageable pedida = paginaPedidaAoService();
        assertThat(pedida.getPageSize()).isEqualTo(20);
        assertThat(pedida.getPageNumber()).isZero();
        assertThat(pedida.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void paginacaoComTamanhoAbsurdoELimitadaAoMaximo() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/transfers").param("size", "5000").param("page", "2")
                .with(user("t").roles("ADMIN")));

        // Then
        resposta.andExpect(status().isOk());
        Pageable pedida = paginaPedidaAoService();
        assertThat(pedida.getPageSize()).isEqualTo(100);
        assertThat(pedida.getPageNumber()).isEqualTo(2);
        assertThat(pedida.getSort()).isEqualTo(Sort.by("id"));
    }

    private Pageable paginaPedidaAoService() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferService).listar(any(), captor.capture());
        return captor.getValue();
    }
}