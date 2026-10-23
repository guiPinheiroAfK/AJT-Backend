package com.AJTBackend.web;

import com.AJTBackend.exception.DocumentoJaCadastradoException;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/*
 * o que testa: os caminhos de erro de /api/passageiros — cada regra do
 * PassageiroRequestDTO (obrigatorio, vazio, tamanho), id inexistente nos 4 verbos,
 * CPF invalido (regra do service) e passageiro em uso na exclusao.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — o caminho triste precisa de teste
 * proprio, com o status HTTP certo pra cada erro, nao so o caminho feliz.
 */
class PassageiroErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"nome\":\"Ana\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X1\"}";

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem nome", "{\"tipoDocumento\":\"CPF\",\"documento\":\"1\"}", "nome: Nome é obrigatório"),
                Arguments.of("nome so com espacos", "{\"nome\":\"   \",\"tipoDocumento\":\"CPF\",\"documento\":\"1\"}", "nome: Nome é obrigatório"),
                Arguments.of("nome maior que 100", "{\"nome\":\"" + "a".repeat(101) + "\",\"tipoDocumento\":\"CPF\",\"documento\":\"1\"}", "100 caracteres"),
                Arguments.of("sem tipo de documento", "{\"nome\":\"Ana\",\"documento\":\"1\"}", "tipoDocumento: Tipo de documento é obrigatório"),
                Arguments.of("tipo maior que 20", "{\"nome\":\"Ana\",\"tipoDocumento\":\"" + "T".repeat(21) + "\",\"documento\":\"1\"}", "20 caracteres"),
                Arguments.of("sem documento", "{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\"}", "documento: Documento é obrigatório"),
                Arguments.of("documento maior que 50", "{\"nome\":\"Ana\",\"tipoDocumento\":\"RG\",\"documento\":\"" + "9".repeat(51) + "\"}", "50 caracteres"),
                Arguments.of("nacionalidade maior que 50", "{\"nome\":\"Ana\",\"tipoDocumento\":\"RG\",\"documento\":\"1\",\"nacionalidade\":\"" + "b".repeat(51) + "\"}", "50 caracteres"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void deveRetornar400SemChamarOService_quandoCadastroTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/passageiros"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(passageiroService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void deveRetornar400SemChamarOService_quandoPutTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(put("/api/passageiros/1"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(passageiroService, never()).atualizar(any(), any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPatchTrazNomeVazio() throws Exception {
        // when
        ResultActions resposta = enviar(patch("/api/passageiros/1"), "{\"nome\":\"\"}");

        // then
        resposta.andExpect(validacao("nome: Nome não pode ser vazio"));
        verify(passageiroService, never()).atualizarParcial(any(), any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPatchTrazDocumentoEmBranco() throws Exception {
        // when
        ResultActions resposta = enviar(patch("/api/passageiros/1"), "{\"documento\":\" \"}");

        // then
        resposta.andExpect(validacao("documento: Documento não pode ser vazio"));
        verify(passageiroService, never()).atualizarParcial(any(), any());
    }

    @ParameterizedTest(name = "corpo \"{0}\" -> 400")
    @ValueSource(strings = {"", "{\"nome\":"})
    void deveRetornar400_quandoCorpoEstaAusenteOuMalformado(String corpo) throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/passageiros"), corpo);

        // then
        resposta.andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
    }

    @Test
    void deveRetornar400_quandoIdNaUrlNaoEhNumero() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/passageiros/abc"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "id"));
    }

    @Test
    void deveRetornar404_quandoGetEmIdInexistente() throws Exception {
        // given
        when(passageiroService.buscarPorId(99L)).thenThrow(new PassageiroNaoEncontradoException(99L));

        // when
        ResultActions resposta = chamar(get("/api/passageiros/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPutEmIdInexistente() throws Exception {
        // given
        when(passageiroService.atualizar(eq(99L), any())).thenThrow(new PassageiroNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(put("/api/passageiros/99"), VALIDO);

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPatchEmIdInexistente() throws Exception {
        // given
        when(passageiroService.atualizarParcial(eq(99L), any())).thenThrow(new PassageiroNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(patch("/api/passageiros/99"), "{\"nome\":\"Bia\"}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoDeleteEmIdInexistente() throws Exception {
        // given
        doThrow(new PassageiroNaoEncontradoException(99L)).when(passageiroService).deletar(99L);

        // when
        ResultActions resposta = chamar(delete("/api/passageiros/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar400DeRegraDeNegocio_quandoCpfEhInvalido() throws Exception {
        // given
        when(passageiroService.criar(any())).thenThrow(new RegraNegocioException("CPF inválido: confira os 11 dígitos."));

        // when
        ResultActions resposta = enviar(post("/api/passageiros"), "{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\",\"documento\":\"123\"}");

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "CPF inválido"));
    }

    @Test
    void deveRetornar400SemRepetirONumero_quandoDocumentoJaEstaCadastrado() throws Exception {
        // given
        when(passageiroService.criar(any())).thenThrow(new DocumentoJaCadastradoException("CPF"));

        // when
        ResultActions resposta = enviar(post("/api/passageiros"), "{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\",\"documento\":\"52998224725\"}");

        // then
        resposta.andExpect(erro(400, "Erro de validação", "Já existe passageiro cadastrado com este CPF"));
    }

    @Test
    void deveRetornar409_quandoExcluiPassageiroEmUso() throws Exception {
        // given
        doThrow(new DataIntegrityViolationException("fk")).when(passageiroService).deletar(1L);

        // when
        ResultActions resposta = chamar(delete("/api/passageiros/1"));

        // then
        resposta.andExpect(erro(409, "Conflito de dados", "em uso"));
    }
}
