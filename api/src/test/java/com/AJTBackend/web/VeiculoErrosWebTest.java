package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.PlacaJaCadastradaException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;

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
                Arguments.of("sem capacidade", "{\"label\":\"Van\",\"placa\":\"ABC1D23\"}", "capacidade: Capacidade é obrigatória"),
                Arguments.of("capacidade zero", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":0}", "capacidade: Capacidade deve ser maior que zero"),
                Arguments.of("capacidade negativa", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":-3}", "capacidade: Capacidade deve ser maior que zero"),
                Arguments.of("tipo maior que 50", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":15,\"tipo\":\"" + "t".repeat(51) + "\"}", "50 caracteres"),
                Arguments.of("marca maior que 50", "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":15,\"marca\":\"" + "m".repeat(51) + "\"}", "50 caracteres"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        enviar(post("/api/veiculos"), json).andExpect(validacao(detalhe));
        verify(veiculoService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        enviar(put("/api/veiculos/1"), json).andExpect(validacao(detalhe));
        verify(veiculoService, never()).atualizar(any(), any());
    }

    @Test
    void capacidadeComTextoRetorna400DeCorpoInvalido() throws Exception {
        enviar(post("/api/veiculos"), "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":\"quinze\"}")
                .andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
    }

    @Test
    void patchRecusaTextoVazioECapacidadeInvalida() throws Exception {
        enviar(patch("/api/veiculos/1"), "{\"placa\":\"\"}").andExpect(validacao("placa: Placa não pode ser vazia"));
        enviar(patch("/api/veiculos/1"), "{\"capacidade\":0}").andExpect(validacao("capacidade: Capacidade deve ser maior que zero"));
        verify(veiculoService, never()).atualizarParcial(any(), any());
    }

    @Test
    void idInexistenteRetorna404NosQuatroVerbos() throws Exception {
        when(veiculoService.buscarPorId(99L)).thenThrow(new VeiculoNaoEncontradoException(99L));
        when(veiculoService.atualizar(eq(99L), any())).thenThrow(new VeiculoNaoEncontradoException(99L));
        when(veiculoService.atualizarParcial(eq(99L), any())).thenThrow(new VeiculoNaoEncontradoException(99L));
        doThrow(new VeiculoNaoEncontradoException(99L)).when(veiculoService).deletar(99L);

        chamar(get("/api/veiculos/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(put("/api/veiculos/99"), VALIDO).andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(patch("/api/veiculos/99"), "{\"marca\":\"Fiat\"}").andExpect(erro(404, "Recurso não encontrado", "99"));
        chamar(delete("/api/veiculos/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void buscaPorPlacaInexistenteRetorna404ESemParametroRetorna400() throws Exception {
        when(veiculoService.buscarPorPlaca("XXX0000")).thenThrow(new VeiculoNaoEncontradoException("XXX0000"));

        chamar(get("/api/veiculos/buscar").param("placa", "XXX0000")).andExpect(erro(404, "Recurso não encontrado", "XXX0000"));
        chamar(get("/api/veiculos/buscar")).andExpect(erro(400, "Parâmetro inválido", "'placa' é obrigatório"));
    }

    @Test
    void placaDuplicadaRetorna400() throws Exception {
        when(veiculoService.criar(any())).thenThrow(new PlacaJaCadastradaException("ABC1D23"));

        enviar(post("/api/veiculos"), VALIDO).andExpect(erro(400, "Erro de validação", "Já existe veículo cadastrado com placa"));
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        when(veiculoService.atualizarParcial(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Veículo", 1L));

        enviar(patch("/api/veiculos/1"), "{\"marca\":\"Fiat\",\"versao\":0}").andExpect(erro(409, "Conflito de edição", "Veículo 1"));
    }

    @Test
    void excluirVeiculoComOrdemDeServicoRetorna409() throws Exception {
        doThrow(new DataIntegrityViolationException("fk")).when(veiculoService).deletar(1L);

        chamar(delete("/api/veiculos/1")).andExpect(erro(409, "Conflito de dados", "em uso"));
    }
}
