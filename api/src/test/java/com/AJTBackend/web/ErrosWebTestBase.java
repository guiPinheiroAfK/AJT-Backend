package com.AJTBackend.web;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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

    // usuario com permissao total: o que interessa aqui e a validacao, nao o perfil
    protected static RequestPostProcessor admin() {
        return user("admin-teste").roles("ADMIN");
    }

    // o "when" dos testes de erro: dispara como admin e devolve a resposta pro "then" conferir
    protected ResultActions enviar(MockHttpServletRequestBuilder requisicao, String json) throws Exception {
        return mockMvc.perform(requisicao.with(admin()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions chamar(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mockMvc.perform(requisicao.with(admin()));
    }

    // pros testes parametrizados por verbo: com json envia o corpo, sem json so chama
    protected ResultActions executar(String verbo, String url, String json) throws Exception {
        MockHttpServletRequestBuilder requisicao = request(HttpMethod.valueOf(verbo), url);
        return json == null ? chamar(requisicao) : enviar(requisicao, json);
    }

    /*
     * devolvem o ResultMatcher em vez de chamar andExpect aqui: o andExpect fica no proprio
     * teste, onde o Sonar (java:S2699) reconhece a verificacao.
     */

    // 400 de validacao do DTO, com o trecho esperado em um dos detalhes ("campo: mensagem")
    protected static ResultMatcher validacao(String trechoDoDetalhe) {
        return ResultMatcher.matchAll(
                status().isBadRequest(),
                jsonPath("$.status").value(400),
                jsonPath("$.erro").value("Erro de validação"),
                jsonPath("$.detalhes", hasItem(containsString(trechoDoDetalhe))));
    }

    // erro no formato padrao: status http, rotulo "erro" e um trecho da mensagem
    protected static ResultMatcher erro(int status, String erro, String trechoDaMensagem) {
        return ResultMatcher.matchAll(
                status().is(status),
                jsonPath("$.status").value(status),
                jsonPath("$.erro").value(erro),
                jsonPath("$.mensagem", containsString(trechoDaMensagem)),
                jsonPath("$.timestamp").exists());
    }
}
