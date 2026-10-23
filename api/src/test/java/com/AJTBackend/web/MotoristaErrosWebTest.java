package com.AJTBackend.web;

import com.AJTBackend.exception.CnhJaCadastradaException;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
 * o que testa: os caminhos de erro de /api/motoristas — regras do MotoristaRequestDTO
 * (obrigatorios, tamanhos, latitude/longitude fora da faixa), id e CNH inexistentes,
 * CNH duplicada, versao velha e motorista em uso por uma OS.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — cada erro com teste proprio e o status HTTP certo.
 */
class MotoristaErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"nome\":\"Carlos\",\"cnh\":\"12345678900\"}";

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem nome", "{\"cnh\":\"1\"}", "nome: Nome é obrigatório"),
                Arguments.of("nome maior que 100", "{\"nome\":\"" + "a".repeat(101) + "\",\"cnh\":\"1\"}", "100 caracteres"),
                Arguments.of("sem cnh", "{\"nome\":\"Carlos\"}", "cnh: CNH é obrigatória"),
                Arguments.of("cnh maior que 20", "{\"nome\":\"Carlos\",\"cnh\":\"" + "1".repeat(21) + "\"}", "20 caracteres"),
                Arguments.of("telefone maior que 20", "{\"nome\":\"Carlos\",\"cnh\":\"1\",\"telefone\":\"" + "9".repeat(21) + "\"}", "20 caracteres"),
                Arguments.of("latitude acima de 90", "{\"nome\":\"Carlos\",\"cnh\":\"1\",\"latitudeAtual\":90.5}", "latitudeAtual: Latitude inválida"),
                Arguments.of("latitude abaixo de -90", "{\"nome\":\"Carlos\",\"cnh\":\"1\",\"latitudeAtual\":-91}", "latitudeAtual: Latitude inválida"),
                Arguments.of("longitude acima de 180", "{\"nome\":\"Carlos\",\"cnh\":\"1\",\"longitudeAtual\":181}", "longitudeAtual: Longitude inválida"),
                Arguments.of("longitude abaixo de -180", "{\"nome\":\"Carlos\",\"cnh\":\"1\",\"longitudeAtual\":-180.1}", "longitudeAtual: Longitude inválida"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/motoristas"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(motoristaService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(put("/api/motoristas/1"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(motoristaService, never()).atualizar(any(), any());
    }

    @Test
    void patchRecusaTextoVazio() throws Exception {
        // When
        ResultActions resposta = enviar(patch("/api/motoristas/1"), "{\"cnh\":\"  \"}");

        // Then
        resposta.andExpect(validacao("cnh: CNH não pode ser vazia"));
        verify(motoristaService, never()).atualizarParcial(any(), any());
    }

    static Stream<Arguments> verbosNoId99() {
        return Stream.of(
                Arguments.of("GET", null),
                Arguments.of("PUT", VALIDO),
                Arguments.of("PATCH", "{\"nome\":\"Dora\"}"),
                Arguments.of("DELETE", null));
    }

    @ParameterizedTest(name = "{0} /api/motoristas/99 -> 404")
    @MethodSource("verbosNoId99")
    void idInexistenteRetorna404EmCadaVerbo(String verbo, String json) throws Exception {
        // Given
        when(motoristaService.buscarPorId(99L)).thenThrow(new MotoristaNaoEncontradoException(99L));
        when(motoristaService.atualizar(eq(99L), any())).thenThrow(new MotoristaNaoEncontradoException(99L));
        when(motoristaService.atualizarParcial(eq(99L), any())).thenThrow(new MotoristaNaoEncontradoException(99L));
        doThrow(new MotoristaNaoEncontradoException(99L)).when(motoristaService).deletar(99L);

        // When
        ResultActions resposta = executar(verbo, "/api/motoristas/99", json);

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void buscaPorCnhInexistenteRetorna404() throws Exception {
        // Given
        when(motoristaService.buscarPorCnh("000")).thenThrow(new MotoristaNaoEncontradoException("000"));

        // When
        ResultActions resposta = chamar(get("/api/motoristas/buscar").param("cnh", "000"));

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "000"));
    }

    @Test
    void buscaPorCnhSemOParametroRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/motoristas/buscar"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'cnh' é obrigatório"));
        verify(motoristaService, never()).buscarPorCnh(any());
    }

    @Test
    void cnhDuplicadaRetorna400() throws Exception {
        // Given
        when(motoristaService.criar(any())).thenThrow(new CnhJaCadastradaException("12345678900"));

        // When
        ResultActions resposta = enviar(post("/api/motoristas"), VALIDO);

        // Then
        resposta.andExpect(erro(400, "Erro de validação", "Já existe motorista cadastrado com CNH"));
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        // Given
        when(motoristaService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Motorista", 1L));

        // When
        ResultActions resposta = enviar(put("/api/motoristas/1"), VALIDO);

        // Then
        resposta.andExpect(erro(409, "Conflito de edição", "alterado por outro usuário"));
    }

    @Test
    void excluirMotoristaComOrdemDeServicoRetorna409() throws Exception {
        // Given
        doThrow(new DataIntegrityViolationException("fk")).when(motoristaService).deletar(1L);

        // When
        ResultActions resposta = chamar(delete("/api/motoristas/1"));

        // Then
        resposta.andExpect(erro(409, "Conflito de dados", "em uso"));
    }
}
