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

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        enviar(post("/api/transfers"), json).andExpect(validacao(detalhe));
        verify(transferService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        enviar(put("/api/transfers/1"), json).andExpect(validacao(detalhe));
        verify(transferService, never()).atualizar(any(), any());
    }

    @Test
    void dataOuHoraEmFormatoErradoRetorna400() throws Exception {
        enviar(post("/api/transfers"), "{\"dataTransfer\":\"2026-02-30\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\"}")
                .andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
        enviar(post("/api/transfers"), "{\"dataTransfer\":\"2026-10-21\",\"horaTransfer\":\"10h\",\"origem\":\"A\",\"destino\":\"B\"}")
                .andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
    }

    @Test
    void filtrosInvalidosRetornam400() throws Exception {
        when(transferService.listar(any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial (2026-10-30) é depois da final (2026-10-01)."));

        chamar(get("/api/transfers").param("dataInicio", "2026-10-30").param("dataFim", "2026-10-01"))
                .andExpect(erro(400, "Regra de negócio", "depois da final"));
        chamar(get("/api/transfers").param("dataFim", "31/10/2026")).andExpect(validacao("dataFim"));
        chamar(get("/api/transfers").param("status", "PERDIDO")).andExpect(validacao("status"));
        chamar(get("/api/transfers").param("semOs", "talvez")).andExpect(validacao("semOs"));
    }

    @Test
    void regrasDoVinculoComAOsRetornam400() throws Exception {
        when(transferService.atualizarParcial(eq(1L), any()))
                .thenThrow(new RegraNegocioException("O transfer é do dia 21/10/2026 e a ordem de serviço #3 é do dia 22/10/2026. A OS agrupa os transfers de um mesmo dia."))
                .thenThrow(new RegraNegocioException("A ordem de serviço #4 está CANCELADA e não recebe transfers. Use uma OS aberta ou em andamento."));

        enviar(patch("/api/transfers/1"), "{\"osId\":3}").andExpect(erro(400, "Regra de negócio", "mesmo dia"));
        enviar(patch("/api/transfers/1"), "{\"osId\":4}").andExpect(erro(400, "Regra de negócio", "CANCELADA"));
    }

    @Test
    void recursosRelacionadosInexistentesRetornam404() throws Exception {
        when(transferService.criar(any()))
                .thenThrow(new OrdemServicoNaoEncontradoException(30L))
                .thenThrow(new PassageiroNaoEncontradoException(31L));

        enviar(post("/api/transfers"), "{" + BASE + ",\"osId\":30}").andExpect(erro(404, "Recurso não encontrado", "30"));
        enviar(post("/api/transfers"), "{" + BASE + ",\"passageiroIds\":[31]}").andExpect(erro(404, "Recurso não encontrado", "31"));
    }

    @Test
    void idInexistenteRetorna404NosVerbosENosPassageiros() throws Exception {
        when(transferService.buscarPorId(99L)).thenThrow(new TransferNaoEncontradoException(99L));
        when(transferService.atualizar(eq(99L), any())).thenThrow(new TransferNaoEncontradoException(99L));
        when(transferService.atualizarParcial(eq(99L), any())).thenThrow(new TransferNaoEncontradoException(99L));
        doThrow(new TransferNaoEncontradoException(99L)).when(transferService).deletar(99L);

        chamar(get("/api/transfers/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
        chamar(get("/api/transfers/99/passageiros")).andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(put("/api/transfers/99"), "{" + BASE + "}").andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(patch("/api/transfers/99"), "{\"origem\":\"C\"}").andExpect(erro(404, "Recurso não encontrado", "99"));
        chamar(delete("/api/transfers/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void cambioForaDoArRetorna502() throws Exception {
        when(transferService.criar(any()))
                .thenThrow(new CotacaoIndisponivelException("USD", "BRL", new RuntimeException("timeout")));

        enviar(post("/api/transfers"), "{" + BASE + ",\"valorOriginal\":100,\"moedaOrigem\":\"USD\"}")
                .andExpect(erro(502, "Servico externo indisponível", "USD"));
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        when(transferService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Transfer", 1L));

        enviar(put("/api/transfers/1"), "{" + BASE + ",\"versao\":2}").andExpect(erro(409, "Conflito de edição", "Transfer 1"));
    }
}
