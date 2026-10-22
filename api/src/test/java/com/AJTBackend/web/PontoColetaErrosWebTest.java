package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.PontoColetaNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        enviar(post("/api/pontos-coleta"), json).andExpect(validacao(detalhe));
        verify(pontoColetaService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        enviar(put("/api/pontos-coleta/1"), json).andExpect(validacao(detalhe));
        verify(pontoColetaService, never()).atualizar(any(), any());
    }

    @Test
    void horarioEmFormatoErradoRetorna400DeCorpoInvalido() throws Exception {
        enviar(post("/api/pontos-coleta"),
                        "{\"transferId\":1,\"localColeta\":\"P\",\"latitude\":0,\"longitude\":0,\"horarioPrevisto\":\"25:99\"}")
                .andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
    }

    @Test
    void patchRecusaLocalVazioECoordenadaForaDaFaixa() throws Exception {
        enviar(patch("/api/pontos-coleta/1"), "{\"localColeta\":\"\"}").andExpect(validacao("localColeta: Local de coleta não pode ser vazio"));
        enviar(patch("/api/pontos-coleta/1"), "{\"latitude\":-90.01}").andExpect(validacao("latitude: Latitude inválida"));
        verify(pontoColetaService, never()).atualizarParcial(any(), any());
    }

    @Test
    void transferInexistenteNoCadastroRetorna404() throws Exception {
        when(pontoColetaService.criar(any())).thenThrow(new TransferNaoEncontradoException(42L));

        enviar(post("/api/pontos-coleta"), VALIDO).andExpect(erro(404, "Recurso não encontrado", "42"));
    }

    @Test
    void idInexistenteRetorna404NosQuatroVerbos() throws Exception {
        when(pontoColetaService.buscarPorId(99L)).thenThrow(new PontoColetaNaoEncontradoException(99L));
        when(pontoColetaService.atualizar(eq(99L), any())).thenThrow(new PontoColetaNaoEncontradoException(99L));
        when(pontoColetaService.atualizarParcial(eq(99L), any())).thenThrow(new PontoColetaNaoEncontradoException(99L));
        doThrow(new PontoColetaNaoEncontradoException(99L)).when(pontoColetaService).deletar(99L);

        chamar(get("/api/pontos-coleta/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(put("/api/pontos-coleta/99"), VALIDO).andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(patch("/api/pontos-coleta/99"), "{\"localColeta\":\"Outro\"}").andExpect(erro(404, "Recurso não encontrado", "99"));
        chamar(delete("/api/pontos-coleta/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
        verify(pontoColetaService).buscarPorId(99L);
        verify(pontoColetaService).deletar(99L);
    }

    @Test
    void transferComTipoErradoNaUrlRetorna400() throws Exception {
        chamar(get("/api/pontos-coleta/transfer/xyz")).andExpect(erro(400, "Parâmetro inválido", "transferId"));
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        when(pontoColetaService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Ponto de coleta", 1L));

        enviar(put("/api/pontos-coleta/1"), VALIDO).andExpect(erro(409, "Conflito de edição", "Ponto de coleta 1"));
    }
}
