package com.AJTBackend.web;

import com.AJTBackend.exception.CnhJaCadastradaException;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
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
        esperaValidacao(enviar(post("/api/motoristas"), json), detalhe);
        verify(motoristaService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        esperaValidacao(enviar(put("/api/motoristas/1"), json), detalhe);
        verify(motoristaService, never()).atualizar(any(), any());
    }

    @Test
    void patchRecusaTextoVazio() throws Exception {
        esperaValidacao(enviar(patch("/api/motoristas/1"), "{\"cnh\":\"  \"}"), "cnh: CNH não pode ser vazia");
        verify(motoristaService, never()).atualizarParcial(any(), any());
    }

    @Test
    void idInexistenteRetorna404NosQuatroVerbos() throws Exception {
        when(motoristaService.buscarPorId(99L)).thenThrow(new MotoristaNaoEncontradoException(99L));
        when(motoristaService.atualizar(eq(99L), any())).thenThrow(new MotoristaNaoEncontradoException(99L));
        when(motoristaService.atualizarParcial(eq(99L), any())).thenThrow(new MotoristaNaoEncontradoException(99L));
        doThrow(new MotoristaNaoEncontradoException(99L)).when(motoristaService).deletar(99L);

        esperaErro(chamar(get("/api/motoristas/99")), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(put("/api/motoristas/99"), VALIDO), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(patch("/api/motoristas/99"), "{\"nome\":\"Dora\"}"), 404, "Recurso não encontrado", "99");
        esperaErro(chamar(delete("/api/motoristas/99")), 404, "Recurso não encontrado", "99");
    }

    @Test
    void buscaPorCnhInexistenteRetorna404ESemParametroRetorna400() throws Exception {
        when(motoristaService.buscarPorCnh("000")).thenThrow(new MotoristaNaoEncontradoException("000"));

        esperaErro(chamar(get("/api/motoristas/buscar").param("cnh", "000")), 404, "Recurso não encontrado", "000");
        esperaErro(chamar(get("/api/motoristas/buscar")), 400, "Parâmetro inválido", "'cnh' é obrigatório");
    }

    @Test
    void cnhDuplicadaRetorna400() throws Exception {
        when(motoristaService.criar(any())).thenThrow(new CnhJaCadastradaException("12345678900"));

        esperaErro(enviar(post("/api/motoristas"), VALIDO), 400, "Erro de validação", "Já existe motorista cadastrado com CNH");
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        when(motoristaService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Motorista", 1L));

        esperaErro(enviar(put("/api/motoristas/1"), VALIDO), 409, "Conflito de edição", "alterado por outro usuário");
    }

    @Test
    void excluirMotoristaComOrdemDeServicoRetorna409() throws Exception {
        doThrow(new DataIntegrityViolationException("fk")).when(motoristaService).deletar(1L);

        esperaErro(chamar(delete("/api/motoristas/1")), 409, "Conflito de dados", "em uso");
    }
}
