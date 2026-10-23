package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.CotacaoIndisponivelException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * o que testa: os caminhos de erro de /api/transfers que o ValidacaoWebTest nao cobre —
 * data/hora ausentes ou mal formatadas, valores negativos ou com casas demais, moeda fora
 * do padrao ISO, passageiros acima do limite, filtros invalidos, as regras do vinculo com
 * a OS (outro dia, OS cancelada), OS/passageiro/transfer inexistentes, cambio fora do ar
 * (502) e versao velha (409).
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — cada erro com teste proprio e o status HTTP certo.
 */
class TransferErrosWebTest extends ErrosWebTestBase {

    private static final String BASE = "\"dataTransfer\":\"2026-10-21\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\"";

    private static String ids(int quantidade) {
        return IntStream.rangeClosed(1, quantidade).mapToObj(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem data", "{\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\"}", "dataTransfer: Data do transfer é obrigatória"),
                Arguments.of("sem hora", "{\"dataTransfer\":\"2026-10-21\",\"origem\":\"A\",\"destino\":\"B\"}", "horaTransfer: Hora do transfer é obrigatória"),
                Arguments.of("destino maior que 100", "{\"dataTransfer\":\"2026-10-21\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"" + "d".repeat(101) + "\"}", "100 caracteres"),
                Arguments.of("valor base negativo", "{" + BASE + ",\"valorBase\":-1}", "valorBase: Valor base não pode ser negativo"),
                Arguments.of("valor base com 3 casas", "{" + BASE + ",\"valorBase\":10.555}", "valorBase: Valor base deve ter até 8 dígitos e 2 casas decimais"),
                Arguments.of("valor original negativo", "{" + BASE + ",\"valorOriginal\":-0.01}", "valorOriginal: Valor original não pode ser negativo"),
                Arguments.of("valor original grande demais", "{" + BASE + ",\"valorOriginal\":123456789}", "valorOriginal: Valor original deve ter até 8 dígitos"),
                Arguments.of("moeda com 2 letras", "{" + BASE + ",\"moedaOrigem\":\"US\"}", "moedaOrigem: Moeda deve ser um código ISO de 3 letras"),
                Arguments.of("moeda com numero", "{" + BASE + ",\"moedaOrigem\":\"U5D\"}", "moedaOrigem: Moeda deve ser um código ISO de 3 letras"),
                Arguments.of("mais de 50 passageiros", "{" + BASE + ",\"passageiroIds\":" + ids(51) + "}", "no máximo 50 passageiros"));
    }

    static Stream<Arguments> filtrosMalFormados() {
        return Stream.of(
                Arguments.of("dataFim", "31/10/2026"),
                Arguments.of("status", "PERDIDO"),
                Arguments.of("semOs", "talvez"));
    }

    static Stream<Arguments> dataOuHoraMalFormadas() {
        return Stream.of(
                Arguments.of("data que nao existe", "{\"dataTransfer\":\"2026-02-30\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\"}"),
                Arguments.of("hora fora do formato", "{\"dataTransfer\":\"2026-10-21\",\"horaTransfer\":\"10h\",\"origem\":\"A\",\"destino\":\"B\"}"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void deveRetornar400SemChamarOService_quandoCadastroTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/transfers"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(transferService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void deveRetornar400SemChamarOService_quandoPutTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(put("/api/transfers/1"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(transferService, never()).atualizar(any(), any());
    }

    @ParameterizedTest(name = "{0} -> 400")
    @MethodSource("dataOuHoraMalFormadas")
    void deveRetornar400DeCorpoInvalido_quandoDataOuHoraEstaoMalFormadas(String caso, String json) throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/transfers"), json);

        // then
        resposta.andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
    }

    @Test
    void deveRetornar400_quandoPeriodoDoFiltroEstaInvertido() throws Exception {
        // given
        when(transferService.listar(any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial (2026-10-30) é depois da final (2026-10-01)."));

        // when
        ResultActions resposta = chamar(get("/api/transfers").param("dataInicio", "2026-10-30").param("dataFim", "2026-10-01"));

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "depois da final"));
    }

    @ParameterizedTest(name = "filtro {0}={1} -> 400")
    @MethodSource("filtrosMalFormados")
    void deveRetornar400_quandoFiltroEstaMalFormado(String parametro, String valor) throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/transfers").param(parametro, valor));

        // then
        resposta.andExpect(validacao(parametro));
    }

    @Test
    void deveRetornar400_quandoOsEhDeOutroDia() throws Exception {
        // given
        when(transferService.atualizarParcial(eq(1L), any()))
                .thenThrow(new RegraNegocioException("O transfer é do dia 21/10/2026 e a ordem de serviço #3 é do dia 22/10/2026. A OS agrupa os transfers de um mesmo dia."));

        // when
        ResultActions resposta = enviar(patch("/api/transfers/1"), "{\"osId\":3}");

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "mesmo dia"));
    }

    @Test
    void deveRetornar400_quandoOsEstaCancelada() throws Exception {
        // given
        when(transferService.atualizarParcial(eq(1L), any()))
                .thenThrow(new RegraNegocioException("A ordem de serviço #4 está CANCELADA e não recebe transfers. Use uma OS aberta ou em andamento."));

        // when
        ResultActions resposta = enviar(patch("/api/transfers/1"), "{\"osId\":4}");

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "CANCELADA"));
    }

    @Test
    void deveRetornar404_quandoCadastroApontaOsInexistente() throws Exception {
        // given
        when(transferService.criar(any())).thenThrow(new OrdemServicoNaoEncontradoException(30L));

        // when
        ResultActions resposta = enviar(post("/api/transfers"), "{" + BASE + ",\"osId\":30}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "30"));
    }

    @Test
    void deveRetornar404_quandoCadastroApontaPassageiroInexistente() throws Exception {
        // given
        when(transferService.criar(any())).thenThrow(new PassageiroNaoEncontradoException(31L));

        // when
        ResultActions resposta = enviar(post("/api/transfers"), "{" + BASE + ",\"passageiroIds\":[31]}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "31"));
    }

    @Test
    void deveRetornar404_quandoGetEmIdInexistente() throws Exception {
        // given
        when(transferService.buscarPorId(99L)).thenThrow(new TransferNaoEncontradoException(99L));

        // when
        ResultActions resposta = chamar(get("/api/transfers/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoListaPassageirosDeTransferInexistente() throws Exception {
        // given
        when(transferService.buscarPorId(99L)).thenThrow(new TransferNaoEncontradoException(99L));

        // when
        ResultActions resposta = chamar(get("/api/transfers/99/passageiros"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPutEmIdInexistente() throws Exception {
        // given
        when(transferService.atualizar(eq(99L), any())).thenThrow(new TransferNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(put("/api/transfers/99"), "{" + BASE + "}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPatchEmIdInexistente() throws Exception {
        // given
        when(transferService.atualizarParcial(eq(99L), any())).thenThrow(new TransferNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(patch("/api/transfers/99"), "{\"origem\":\"C\"}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoDeleteEmIdInexistente() throws Exception {
        // given
        doThrow(new TransferNaoEncontradoException(99L)).when(transferService).deletar(99L);

        // when
        ResultActions resposta = chamar(delete("/api/transfers/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar502_quandoCambioEstaForaDoAr() throws Exception {
        // given
        when(transferService.criar(any()))
                .thenThrow(new CotacaoIndisponivelException("USD", "BRL", new RuntimeException("timeout")));

        // when
        ResultActions resposta = enviar(post("/api/transfers"), "{" + BASE + ",\"valorOriginal\":100,\"moedaOrigem\":\"USD\"}");

        // then
        resposta.andExpect(erro(502, "Serviço externo indisponível", "USD"));
    }

    @Test
    void deveRetornar409_quandoPutTrazVersaoVelha() throws Exception {
        // given
        when(transferService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Transfer", 1L));

        // when
        ResultActions resposta = enviar(put("/api/transfers/1"), "{" + BASE + ",\"versao\":2}");

        // then
        resposta.andExpect(erro(409, "Conflito de edição", "Transfer 1"));
    }
}
