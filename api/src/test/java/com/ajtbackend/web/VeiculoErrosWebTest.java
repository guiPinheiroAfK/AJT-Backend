package com.ajtbackend.web;

import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.PlacaJaCadastradaException;
import com.ajtbackend.exception.VeiculoNaoEncontradoException;
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
 * o que testa: os caminhos de erro de /api/veiculos — regras do VeiculoRequestDTO
 * (obrigatorios, tamanhos, capacidade zero/negativa/ausente/nao numerica), id e placa
 * inexistentes, placa duplicada, versao velha e veiculo em uso.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — cada erro com teste proprio e o status HTTP certo.
 */
class VeiculoErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"label\":\"Van 01\",\"placa\":\"ABC1D23\",\"capacidade\":15}";

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem label", "{\"placa\":\"ABC1D23\",\"capacidade\":15}", "label: Label é obrigatório"),
                Arguments.of("label maior que 50", "{\"label\":\"" + "v".repeat(51) + "\",\"placa\":\"ABC1D23\",\"capacidade\":15}", "50 caracteres"),
                Arguments.of("sem placa", "{\"label\":\"Van\",\"capacidade\":15}", "placa: Placa é obrigatória"),
                Arguments.of("placa maior que 10", "{\"label\":\"Van\",\"placa\":\"ABCDEFGHIJK\",\"capacidade\":15}", "10 caracteres"),
                Arguments.of("placa curta", "{\"label\":\"Van\",\"placa\":\"XX12\",\"capacidade\":15}", "placa: Placa inválida"),
                Arguments.of("placa fora do padrao", "{\"label\":\"Van\",\"placa\":\"1234ABC\",\"capacidade\":15}", "placa: Placa inválida"),
                Arguments.of("sem capacidade", "{\"label\":\"Van\",\"placa\":\"ABC1D23\"}", "capacidade: Capacidade é obrigatória"),
                Arguments.of("capacidade zero", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":0}", "capacidade: Capacidade deve ser maior que zero"),
                Arguments.of("capacidade negativa", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":-3}", "capacidade: Capacidade deve ser maior que zero"),
                Arguments.of("tipo maior que 50", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":15,\"tipo\":\"" + "t".repeat(51) + "\"}", "50 caracteres"),
                Arguments.of("marca maior que 50", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":15,\"marca\":\"" + "m".repeat(51) + "\"}", "50 caracteres"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void deveRetornar400SemChamarOService_quandoCadastroTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/veiculos"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(veiculoService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void deveRetornar400SemChamarOService_quandoPutTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(put("/api/veiculos/1"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(veiculoService, never()).atualizar(any(), any());
    }

    @Test
    void deveRetornar400DeCorpoInvalido_quandoCapacidadeVemComoTexto() throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/veiculos"), "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":\"quinze\"}");

        // then
        resposta.andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPatchTrazPlacaVazia() throws Exception {
        // when
        ResultActions resposta = enviar(patch("/api/veiculos/1"), "{\"placa\":\"\"}");

        // then
        resposta.andExpect(validacao("placa: Placa não pode ser vazia"));
        verify(veiculoService, never()).atualizarParcial(any(), any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPatchTrazCapacidadeZero() throws Exception {
        // when
        ResultActions resposta = enviar(patch("/api/veiculos/1"), "{\"capacidade\":0}");

        // then
        resposta.andExpect(validacao("capacidade: Capacidade deve ser maior que zero"));
        verify(veiculoService, never()).atualizarParcial(any(), any());
    }

    @Test
    void deveRetornar404_quandoGetEmIdInexistente() throws Exception {
        // given
        when(veiculoService.buscarPorId(99L)).thenThrow(new VeiculoNaoEncontradoException(99L));

        // when
        ResultActions resposta = chamar(get("/api/veiculos/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPutEmIdInexistente() throws Exception {
        // given
        when(veiculoService.atualizar(eq(99L), any())).thenThrow(new VeiculoNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(put("/api/veiculos/99"), VALIDO);

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPatchEmIdInexistente() throws Exception {
        // given
        when(veiculoService.atualizarParcial(eq(99L), any())).thenThrow(new VeiculoNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(patch("/api/veiculos/99"), "{\"marca\":\"Fiat\"}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoDeleteEmIdInexistente() throws Exception {
        // given
        doThrow(new VeiculoNaoEncontradoException(99L)).when(veiculoService).deletar(99L);

        // when
        ResultActions resposta = chamar(delete("/api/veiculos/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoBuscaPorPlacaInexistente() throws Exception {
        // given
        when(veiculoService.buscarPorPlaca("XXX0000")).thenThrow(new VeiculoNaoEncontradoException("XXX0000"));

        // when
        ResultActions resposta = chamar(get("/api/veiculos/buscar").param("placa", "XXX0000"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "XXX0000"));
    }

    @Test
    void deveRetornar400_quandoBuscaPorPlacaSemOParametro() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/veiculos/buscar"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'placa' é obrigatório"));
    }

    @Test
    void deveRetornar400_quandoPlacaJaEstaCadastrada() throws Exception {
        // given
        when(veiculoService.criar(any())).thenThrow(new PlacaJaCadastradaException("ABC1D23"));

        // when
        ResultActions resposta = enviar(post("/api/veiculos"), VALIDO);

        // then
        resposta.andExpect(erro(400, "Erro de validação", "Já existe veículo cadastrado com placa"));
    }

    @Test
    void deveRetornar409_quandoPatchTrazVersaoVelha() throws Exception {
        // given
        when(veiculoService.atualizarParcial(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Veículo", 1L));

        // when
        ResultActions resposta = enviar(patch("/api/veiculos/1"), "{\"marca\":\"Fiat\",\"versao\":0}");

        // then
        resposta.andExpect(erro(409, "Conflito de edição", "Veículo 1"));
    }

    @Test
    void deveRetornar409_quandoExcluiVeiculoComOrdemDeServico() throws Exception {
        // given
        doThrow(new DataIntegrityViolationException("fk")).when(veiculoService).deletar(1L);

        // when
        ResultActions resposta = chamar(delete("/api/veiculos/1"));

        // then
        resposta.andExpect(erro(409, "Conflito de dados", "em uso"));
    }
}
