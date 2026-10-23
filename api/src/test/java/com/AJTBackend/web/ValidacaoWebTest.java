package com.AJTBackend.web;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

/*
 * o que testa: a validacao na fronteira HTTP: campos obrigatorios e tamanho do
 * transfer, enum invalido listando os aceitos, json malformado, PATCH aceitando
 * campo ausente e recusando texto vazio, tipo errado na url, login sem campos e
 * a paginacao padrao (20 por pagina, maximo 100, ordenada por id).
 *
 * como rodar: @WebMvcTest (WebTestBase), services mockados. sem banco. "mvn test".
 *
 * por que existe: dado invalido tem que parar no controller com 400 e o detalhe
 * do campo, sem nunca chegar no service nem no banco.
 */
class ValidacaoWebTest extends WebTestBase {

    private static final String DATA_E_HORA = "\"dataTransfer\":\"2026-09-20\",\"horaTransfer\":\"14:30:00\"";

    private static String corpoTransfer(String camposExtras) {
        return "{" + DATA_E_HORA + "," + camposExtras + "}";
    }

    // manda o json como ADMIN (aqui o que interessa e a validacao, nao o perfil)
    private ResultActions enviarComoAdmin(MockHttpServletRequestBuilder requisicao, String json) throws Exception {
        return mockMvc.perform(requisicao.with(user("t").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ----- criacao de transfer: campos invalidos -----

    @Test
    void deveRetornar400ComDetalhesSemChamarOService_quandoFaltamCamposObrigatorios() throws Exception {
        // given
        // origem vazia e destino ausente
        String corpo = corpoTransfer("\"origem\":\"\"");

        // when
        ResultActions resposta = enviarComoAdmin(post("/api/transfers"), corpo);

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Erro de validação"))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("destino"))));
        verify(transferService, never()).criar(any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoCampoEhMaiorQueOBanco() throws Exception {
        // given
        String origemGigante = "x".repeat(101);
        String corpo = corpoTransfer("\"origem\":\"" + origemGigante + "\",\"destino\":\"Hotel\"");

        // when
        ResultActions resposta = enviarComoAdmin(post("/api/transfers"), corpo);

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("100 caracteres"))));
        verify(transferService, never()).criar(any());
    }

    @Test
    void deveListarOsValoresAceitos_quandoEnumEhInvalido() throws Exception {
        // given
        String corpo = corpoTransfer("\"origem\":\"A\",\"destino\":\"B\",\"status\":\"XPTO\"");

        // when
        ResultActions resposta = enviarComoAdmin(post("/api/transfers"), corpo);

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes[0]", containsString("status")))
                .andExpect(jsonPath("$.detalhes[0]", containsString("AGUARDANDO_OS")));
        verify(transferService, never()).criar(any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoJsonEstaMalformado() throws Exception {
        // given
        String corpoInvalido = "{nao e json";

        // when
        ResultActions resposta = enviarComoAdmin(post("/api/transfers"), corpoInvalido);

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Requisição inválida"));
        verify(transferService, never()).criar(any());
    }

    // ----- PATCH -----

    @Test
    void deveAceitar_quandoPatchTrazSoUmCampo() throws Exception {
        // given
        // so o status, sem nenhum outro campo
        String corpo = "{\"status\":\"CONFIRMADO\"}";

        // when
        ResultActions resposta = enviarComoAdmin(patch("/api/transfers/1"), corpo);

        // then
        resposta.andExpect(status().isOk());
        verify(transferService).atualizarParcial(eq(1L), any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPatchTrazTextoSoComEspacos() throws Exception {
        // given
        String corpo = "{\"origem\":\"   \"}";

        // when
        ResultActions resposta = enviarComoAdmin(patch("/api/transfers/1"), corpo);

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("origem"))));
        verify(transferService, never()).atualizarParcial(anyLong(), any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoMoedaNaoEhCodigoIso() throws Exception {
        // given
        String corpo = "{\"moedaOrigem\":\"DOLAR\"}";

        // when
        ResultActions resposta = enviarComoAdmin(patch("/api/transfers/1"), corpo);

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString("moedaOrigem"))));
        verify(transferService, never()).atualizarParcial(anyLong(), any());
    }

    // ----- parametros da url -----

    @Test
    void deveRetornar400SemChamarOService_quandoIdTemTipoErrado() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/transfers/abc").with(user("t").roles("ADMIN")));

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem", containsString("abc")));
        verify(transferService, never()).buscarPorId(anyLong());
    }

    @Test
    void deveRetornar404EmJson_quandoRotaNaoExiste() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/nao-existe").with(user("t").roles("ADMIN")));

        // then
        resposta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deveRetornar400SemChamarOService_quandoCodigoDaMoedaDaCotacaoEhInvalido() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/cotacao").param("de", "US").param("para", "BRL")
                .with(user("t").roles("ATENDENTE")));

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(cotacaoService);
    }

    @Test
    void deveRetornar400SemChamarOService_quandoStatusDaBuscaEhInvalido() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/transfers/buscar").param("status", "XPTO")
                .with(user("t").roles("ADMIN")));

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(transferService);
    }

    // ----- login -----

    @Test
    void deveRetornar400ComUmDetalhePorCampo_quandoLoginVemSemCampos() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalhes.length()").value(2));
        verifyNoInteractions(authService);
    }

    // ----- paginacao -----

    @Test
    void deveUsarTamanhoPadraoOrdenadoPorId_quandoListaSemParametros() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/transfers").with(user("t").roles("ADMIN")));

        // then
        resposta.andExpect(status().isOk());
        Pageable pedida = paginaPedidaAoService();
        assertThat(pedida.getPageSize()).isEqualTo(20);
        assertThat(pedida.getPageNumber()).isZero();
        assertThat(pedida.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void deveLimitarAoMaximo_quandoTamanhoDaPaginaEhAbsurdo() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/transfers").param("size", "5000").param("page", "2")
                .with(user("t").roles("ADMIN")));

        // then
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
