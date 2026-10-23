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
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/transfers"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(transferService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(put("/api/transfers/1"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(transferService, never()).atualizar(any(), any());
    }

    @ParameterizedTest(name = "{0} -> 400")
    @CsvSource(delimiter = '|', value = {
            "data inexistente (30/02) | \"dataTransfer\":\"2026-02-30\",\"horaTransfer\":\"10:00:00\"",
            "hora fora do formato     | \"dataTransfer\":\"2026-10-21\",\"horaTransfer\":\"10h\""})
    void dataOuHoraEmFormatoErradoRetorna400(String caso, String dataEHora) throws Exception {
        // Given
        String corpo = "{" + dataEHora + ",\"origem\":\"A\",\"destino\":\"B\"}";

        // When
        ResultActions resposta = enviar(post("/api/transfers"), corpo);

        // Then
        resposta.andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
        verify(transferService, never()).criar(any());
    }

    @Test
    void filtroComPeriodoInvertidoRetorna400() throws Exception {
        // Given
        when(transferService.listar(any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial (2026-10-30) é depois da final (2026-10-01)."));

        // When
        ResultActions resposta = chamar(get("/api/transfers").param("dataInicio", "2026-10-30").param("dataFim", "2026-10-01"));

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "depois da final"));
    }

    @ParameterizedTest(name = "{0}={1} -> 400")
    @CsvSource({"dataFim, 31/10/2026", "status, PERDIDO", "semOs, talvez"})
    void filtroComValorDeTipoErradoRetorna400(String parametro, String valor) throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/transfers").param(parametro, valor));

        // Then
        resposta.andExpect(validacao(parametro));
        verify(transferService, never()).listar(any(), any());
    }

    @Test
    void vincularAOsDeOutroDiaRetorna400() throws Exception {
        // Given
        when(transferService.atualizarParcial(eq(1L), any())).thenThrow(new RegraNegocioException(
                "O transfer é do dia 21/10/2026 e a ordem de serviço #3 é do dia 22/10/2026. A OS agrupa os transfers de um mesmo dia."));

        // When
        ResultActions resposta = enviar(patch("/api/transfers/1"), "{\"osId\":3}");

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "mesmo dia"));
    }

    @Test
    void vincularAOsCanceladaRetorna400() throws Exception {
        // Given
        when(transferService.atualizarParcial(eq(1L), any())).thenThrow(new RegraNegocioException(
                "A ordem de serviço #4 está CANCELADA e não recebe transfers. Use uma OS aberta ou em andamento."));

        // When
        ResultActions resposta = enviar(patch("/api/transfers/1"), "{\"osId\":4}");

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "CANCELADA"));
    }

    @Test
    void cadastroComOsInexistenteRetorna404() throws Exception {
        // Given
        when(transferService.criar(any())).thenThrow(new OrdemServicoNaoEncontradoException(30L));

        // When
        ResultActions resposta = enviar(post("/api/transfers"), "{" + BASE + ",\"osId\":30}");

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "30"));
    }

    @Test
    void cadastroComPassageiroInexistenteRetorna404() throws Exception {
        // Given
        when(transferService.criar(any())).thenThrow(new PassageiroNaoEncontradoException(31L));

        // When
        ResultActions resposta = enviar(post("/api/transfers"), "{" + BASE + ",\"passageiroIds\":[31]}");

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "31"));
    }

    static Stream<Arguments> requisicoesAoTransfer99() {
        return Stream.of(
                Arguments.of("GET", "/api/transfers/99", null),
                Arguments.of("GET", "/api/transfers/99/passageiros", null),
                Arguments.of("PUT", "/api/transfers/99", "{" + BASE + "}"),
                Arguments.of("PATCH", "/api/transfers/99", "{\"origem\":\"C\"}"),
                Arguments.of("DELETE", "/api/transfers/99", null));
    }

    @ParameterizedTest(name = "{0} {1} -> 404")
    @MethodSource("requisicoesAoTransfer99")
    void transferInexistenteRetorna404InclusiveNosPassageiros(String verbo, String url, String json) throws Exception {
        // Given
        when(transferService.buscarPorId(99L)).thenThrow(new TransferNaoEncontradoException(99L));
        when(transferService.atualizar(eq(99L), any())).thenThrow(new TransferNaoEncontradoException(99L));
        when(transferService.atualizarParcial(eq(99L), any())).thenThrow(new TransferNaoEncontradoException(99L));
        doThrow(new TransferNaoEncontradoException(99L)).when(transferService).deletar(99L);

        // When
        ResultActions resposta = executar(verbo, url, json);

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void cambioForaDoArRetorna502() throws Exception {
        // Given
        when(transferService.criar(any()))
                .thenThrow(new CotacaoIndisponivelException("USD", "BRL", new RuntimeException("timeout")));

        // When
        ResultActions resposta = enviar(post("/api/transfers"), "{" + BASE + ",\"valorOriginal\":100,\"moedaOrigem\":\"USD\"}");

        // Then
        resposta.andExpect(erro(502, "Servico externo indisponível", "USD"));
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        // Given
        when(transferService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Transfer", 1L));

        // When
        ResultActions resposta = enviar(put("/api/transfers/1"), "{" + BASE + ",\"versao\":2}");

        // Then
        resposta.andExpect(erro(409, "Conflito de edição", "Transfer 1"));
    }
}
