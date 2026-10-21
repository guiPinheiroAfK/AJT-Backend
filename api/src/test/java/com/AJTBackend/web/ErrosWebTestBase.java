package com.AJTBackend.web;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * base dos testes de "caminho triste" por recurso (*ErrosWebTest): cada classe filha
 * cobre, pra um controller, DTO invalido (400 com o campo nos detalhes), id que nao
 * existe (404), registro duplicado / regra de negocio (400), registro em uso e versao
 * velha (409) e parametro obrigatorio ausente (400) — sempre conferindo o status HTTP
 * E o corpo no formato do ErroResponseDTO, que e o que o front le.
 *
 * os services sao mocks (WebTestBase): aqui se testa a fronteira HTTP (validacao do
 * DTO, traducao de excecao em status). a regra em si e testada nos *ServiceTest.
 */
abstract class ErrosWebTestBase extends WebTestBase {

    /** usuario com permissao total: o que interessa aqui e a validacao, nao o perfil. */
    protected static RequestPostProcessor admin() {
        return user("admin-teste").roles("ADMIN");
    }

    protected ResultActions enviar(MockHttpServletRequestBuilder requisicao, String json) throws Exception {
        return mockMvc.perform(requisicao.with(admin()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions chamar(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mockMvc.perform(requisicao.with(admin()));
    }

    /** 400 de validacao do DTO, com o trecho esperado em um dos detalhes ("campo: mensagem"). */
    protected static void esperaValidacao(ResultActions resposta, String trechoDoDetalhe) throws Exception {
        resposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Erro de validação"))
                .andExpect(jsonPath("$.detalhes", hasItem(containsString(trechoDoDetalhe))));
    }

    /** erro no formato padrao: status http, rotulo "erro" e um trecho da mensagem. */
    protected static void esperaErro(ResultActions resposta, int status, String erro, String trechoDaMensagem) throws Exception {
        resposta.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.erro").value(erro))
                .andExpect(jsonPath("$.mensagem", containsString(trechoDaMensagem)))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
