package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.PontoColetaNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/*
 * o que testa: os caminhos de erro de /api/pontos-coleta — regras do PontoColetaRequestDTO
 * (transfer e coordenadas obrigatorios, faixas de latitude/longitude, ordem positiva,
 * horario em formato errado), transfer ou ponto inexistentes e versao velha.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — cada erro com teste proprio e o status HTTP certo.
 */
class PontoColetaErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"transferId\":1,\"localColeta\":\"Portao 2\",\"latitude\":-25.5,\"longitude\":-54.5}";

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem transfer", "{\"localColeta\":\"P\",\"latitude\":0,\"longitude\":0}", "transferId: Transfer é obrigatório"),
                Arguments.of("sem local", "{\"transferId\":1,\"latitude\":0,\"longitude\":0}", "localColeta: Local de coleta é obrigatório"),
                Arguments.of("local maior que 100", "{\"transferId\":1,\"localColeta\":\"" + "p".repeat(101) + "\",\"latitude\":0,\"longitude\":0}", "100 caracteres"),
                Arguments.of("ordem zero", "{\"transferId\":1,\"localColeta\":\"P\",\"ordemParada\":0,\"latitude\":0,\"longitude\":0}", "ordemParada: Ordem da parada deve ser maior que zero"),
                Arguments.of("sem latitude", "{\"transferId\":1,\"localColeta\":\"P\",\"longitude\":0}", "latitude: Latitude é obrigatória"),
                Arguments.of("latitude acima de 90", "{\"transferId\":1,\"localColeta\":\"P\",\"latitude\":91,\"longitude\":0}", "latitude: Latitude inválida"),
                Arguments.of("sem longitude", "{\"transferId\":1,\"localColeta\":\"P\",\"latitude\":0}", "longitude: Longitude é obrigatória"),
                Arguments.of("longitude abaixo de -180", "{\"transferId\":1,\"localColeta\":\"P\",\"latitude\":0,\"longitude\":-181}", "longitude: Longitude inválida"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/pontos-coleta"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(pontoColetaService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(put("/api/pontos-coleta/1"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(pontoColetaService, never()).atualizar(any(), any());
    }

    @Test
    void horarioEmFormatoErradoRetorna400DeCorpoInvalido() throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/pontos-coleta"),
                        "{\"transferId\":1,\"localColeta\":\"P\",\"latitude\":0,\"longitude\":0,\"horarioPrevisto\":\"25:99\"}");

        // Then
        resposta.andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
    }

    static Stream<Arguments> patchesInvalidos() {
        return Stream.of(
                Arguments.of("{\"localColeta\":\"\"}", "localColeta: Local de coleta não pode ser vazio"),
                Arguments.of("{\"latitude\":-90.01}", "latitude: Latitude inválida"));
    }

    @ParameterizedTest(name = "PATCH {0} -> 400")
    @MethodSource("patchesInvalidos")
    void patchComLocalVazioOuCoordenadaForaDaFaixaRetorna400(String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(patch("/api/pontos-coleta/1"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(pontoColetaService, never()).atualizarParcial(any(), any());
    }

    @Test
    void transferInexistenteNoCadastroRetorna404() throws Exception {
        // Given
        when(pontoColetaService.criar(any())).thenThrow(new TransferNaoEncontradoException(42L));

        // When
        ResultActions resposta = enviar(post("/api/pontos-coleta"), VALIDO);

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "42"));
    }

    static Stream<Arguments> verbosNoId99() {
        return Stream.of(
                Arguments.of("GET", null),
                Arguments.of("PUT", VALIDO),
                Arguments.of("PATCH", "{\"localColeta\":\"Outro\"}"),
                Arguments.of("DELETE", null));
    }

    @ParameterizedTest(name = "{0} /api/pontos-coleta/99 -> 404")
    @MethodSource("verbosNoId99")
    void idInexistenteRetorna404EmCadaVerbo(String verbo, String json) throws Exception {
        // Given
        when(pontoColetaService.buscarPorId(99L)).thenThrow(new PontoColetaNaoEncontradoException(99L));
        when(pontoColetaService.atualizar(eq(99L), any())).thenThrow(new PontoColetaNaoEncontradoException(99L));
        when(pontoColetaService.atualizarParcial(eq(99L), any())).thenThrow(new PontoColetaNaoEncontradoException(99L));
        doThrow(new PontoColetaNaoEncontradoException(99L)).when(pontoColetaService).deletar(99L);

        // When
        ResultActions resposta = executar(verbo, "/api/pontos-coleta/99", json);

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void transferComTipoErradoNaUrlRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/pontos-coleta/transfer/xyz"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "transferId"));
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        // Given
        when(pontoColetaService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Ponto de coleta", 1L));

        // When
        ResultActions resposta = enviar(put("/api/pontos-coleta/1"), VALIDO);

        // Then
        resposta.andExpect(erro(409, "Conflito de edição", "Ponto de coleta 1"));
    }
}
