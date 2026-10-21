package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.ParadaOsNaoEncontradaException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/*
 * o que testa: os caminhos de erro de /api/paradas-os — regras do ParadaOsRequestDTO
 * (OS e ordem obrigatorias, ordem positiva, local, coordenadas, limite de transfers,
 * acao/status fora do enum), OS/transfer/parada inexistentes, motorista tentando mexer
 * em outro campo alem do status (403, regra do service) e versao velha.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — cada erro com teste proprio e o status HTTP certo.
 */
class ParadaOsErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"Aeroporto\"}";

    private static String transferIds(int quantidade) {
        return IntStream.rangeClosed(1, quantidade).mapToObj(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem OS", "{\"ordemParada\":1,\"localParada\":\"A\"}", "osId: Ordem de serviço é obrigatória"),
                Arguments.of("sem ordem", "{\"osId\":1,\"localParada\":\"A\"}", "ordemParada: Ordem da parada é obrigatória"),
                Arguments.of("ordem negativa", "{\"osId\":1,\"ordemParada\":-1,\"localParada\":\"A\"}", "ordemParada: Ordem da parada deve ser maior que zero"),
                Arguments.of("sem local", "{\"osId\":1,\"ordemParada\":1}", "localParada: Local da parada é obrigatório"),
                Arguments.of("local maior que 100", "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"" + "l".repeat(101) + "\"}", "100 caracteres"),
                Arguments.of("latitude acima de 90", "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"A\",\"latitude\":100}", "latitude: Latitude inválida"),
                Arguments.of("longitude acima de 180", "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"A\",\"longitude\":200}", "longitude: Longitude inválida"),
                Arguments.of("mais de 100 transfers", "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"A\",\"transferIds\":" + transferIds(101) + "}",
                        "no máximo 100 transfers"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        esperaValidacao(enviar(post("/api/paradas-os"), json), detalhe);
        verify(paradaOsService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        esperaValidacao(enviar(put("/api/paradas-os/1"), json), detalhe);
        verify(paradaOsService, never()).atualizar(any(), any());
    }

    @Test
    void acaoEStatusForaDoEnumListamOsAceitos() throws Exception {
        esperaValidacao(enviar(post("/api/paradas-os"), "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"A\",\"acao\":\"PULAR\"}"),
                "acao: valor 'PULAR' inválido. Aceitos: EMBARQUE, DESEMBARQUE");
        esperaValidacao(enviar(patch("/api/paradas-os/1"), "{\"statusParada\":\"FEITA\"}"),
                "statusParada: valor 'FEITA' inválido. Aceitos: PENDENTE, EM_ANDAMENTO, CONCLUIDA, CANCELADA");
    }

    @Test
    void osOuTransferInexistenteNoCadastroRetorna404() throws Exception {
        when(paradaOsService.criar(any()))
                .thenThrow(new OrdemServicoNaoEncontradoException(8L))
                .thenThrow(new TransferNaoEncontradoException(9L));

        esperaErro(enviar(post("/api/paradas-os"), VALIDO), 404, "Recurso não encontrado", "8");
        esperaErro(enviar(post("/api/paradas-os"), "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"A\",\"transferIds\":[9]}"),
                404, "Recurso não encontrado", "9");
    }

    @Test
    void idInexistenteRetorna404() throws Exception {
        when(paradaOsService.buscarPorId(99L)).thenThrow(new ParadaOsNaoEncontradaException(99L));
        when(paradaOsService.atualizarParcial(eq(99L), any())).thenThrow(new ParadaOsNaoEncontradaException(99L));
        when(paradaOsService.listarPorOrdemServico(98L)).thenThrow(new OrdemServicoNaoEncontradoException(98L));
        doThrow(new ParadaOsNaoEncontradaException(99L)).when(paradaOsService).deletar(99L);

        esperaErro(chamar(get("/api/paradas-os/99")), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(patch("/api/paradas-os/99"), "{\"statusParada\":\"CONCLUIDA\"}"), 404, "Recurso não encontrado", "99");
        esperaErro(chamar(get("/api/paradas-os/ordem-servico/98")), 404, "Recurso não encontrado", "98");
        esperaErro(chamar(delete("/api/paradas-os/99")), 404, "Recurso não encontrado", "99");
    }

    @Test
    void motoristaMexendoEmOutroCampoRetorna403() throws Exception {
        when(paradaOsService.atualizarParcial(eq(1L), any()))
                .thenThrow(new AccessDeniedException("Motorista só pode alterar o status da parada"));

        esperaErro(mockMvc.perform(patch("/api/paradas-os/1").with(user("mot").roles("MOTORISTA"))
                        .contentType("application/json").content("{\"localParada\":\"Outro lugar\"}")),
                403, "Acesso negado", "só pode alterar o status");
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        when(paradaOsService.atualizarParcial(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Parada", 1L));

        esperaErro(enviar(patch("/api/paradas-os/1"), "{\"statusParada\":\"CONCLUIDA\",\"versao\":0}"),
                409, "Conflito de edição", "Parada 1");
    }

    @Test
    void listaVaziaDeTransfersEhAceita() throws Exception {
        when(paradaOsService.criar(any())).thenReturn(null);

        enviar(post("/api/paradas-os"), "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"A\",\"transferIds\":[]}")
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }
}
