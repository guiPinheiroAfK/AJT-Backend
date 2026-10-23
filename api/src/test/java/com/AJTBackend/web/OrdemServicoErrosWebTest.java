package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
 * o que testa: os caminhos de erro de /api/ordens-servico — data obrigatoria e em
 * formato valido, status fora do enum (no corpo e na url), filtro com data invalida
 * ou periodo invertido, motorista/veiculo/OS inexistentes, regra de negocio (mudar a
 * data de OS com transfers), versao velha, OS com transfers na exclusao e o PDF de
 * uma OS que nao existe.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — cada erro com teste proprio e o status HTTP certo.
 */
class OrdemServicoErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"dataServico\":\"2026-10-21\"}";

    @Test
    void cadastroSemDataRetorna400() throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{}");

        // Then
        resposta.andExpect(validacao("dataServico: Data do serviço é obrigatória"));
        verify(ordemServicoService, never()).criar(any());
    }

    @Test
    void edicaoSemDataRetorna400() throws Exception {
        // When
        ResultActions resposta = enviar(put("/api/ordens-servico/1"), "{\"status\":\"ABERTA\"}");

        // Then
        resposta.andExpect(validacao("dataServico: Data do serviço é obrigatória"));
        verify(ordemServicoService, never()).atualizar(any(), any());
    }

    @Test
    void dataForaDoFormatoIsoRetorna400() throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"21/10/2026\"}");

        // Then
        resposta.andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
        verify(ordemServicoService, never()).criar(any());
    }

    @Test
    void statusForaDoEnumNoCorpoListaOsAceitos() throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"status\":\"PAUSADA\"}");

        // Then
        resposta.andExpect(validacao("status: valor 'PAUSADA' inválido. Aceitos: ABERTA, EM_ANDAMENTO, FINALIZADA, CANCELADA"));
    }

    @Test
    void statusForaDoEnumNaUrlRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/ordens-servico/buscar").param("status", "PAUSADA"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "status"));
    }

    @Test
    void buscaPorStatusSemOParametroRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/ordens-servico/buscar"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'status' é obrigatório"));
    }

    @Test
    void filtroComPeriodoInvertidoRetorna400() throws Exception {
        // Given
        when(ordemServicoService.listar(any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial (2026-10-30) é depois da final (2026-10-01)."));

        // When
        ResultActions resposta = chamar(get("/api/ordens-servico").param("dataInicio", "2026-10-30").param("dataFim", "2026-10-01"));

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "depois da final"));
    }

    @ParameterizedTest(name = "{0}={1} -> 400")
    @CsvSource({"dataInicio, ontem", "motoristaId, um"})
    void filtroComValorDeTipoErradoRetorna400(String parametro, String valor) throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/ordens-servico").param(parametro, valor));

        // Then
        resposta.andExpect(validacao(parametro));
        verify(ordemServicoService, never()).listar(any(), any());
    }

    @Test
    void minhasComDataInvalidaRetorna400() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/minhas").param("data", "hoje")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("mot").roles("MOTORISTA")));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "data"));
    }

    @Test
    void cadastroComMotoristaInexistenteRetorna404() throws Exception {
        // Given
        when(ordemServicoService.criar(any())).thenThrow(new MotoristaNaoEncontradoException(5L));

        // When
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"motoristaId\":5}");

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "5"));
    }

    @Test
    void cadastroComVeiculoInexistenteRetorna404() throws Exception {
        // Given
        when(ordemServicoService.criar(any())).thenThrow(new VeiculoNaoEncontradoException(6L));

        // When
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"veiculoId\":6}");

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "6"));
    }

    static Stream<Arguments> requisicoesNaOs99() {
        return Stream.of(
                Arguments.of("GET", "/api/ordens-servico/99", null),
                Arguments.of("PUT", "/api/ordens-servico/99", VALIDO),
                Arguments.of("PATCH", "/api/ordens-servico/99", "{\"status\":\"CANCELADA\"}"),
                Arguments.of("GET", "/api/ordens-servico/99/pdf", null),
                Arguments.of("DELETE", "/api/ordens-servico/99", null));
    }

    @ParameterizedTest(name = "{0} {1} -> 404")
    @MethodSource("requisicoesNaOs99")
    void osInexistenteRetorna404InclusiveNoPdf(String verbo, String url, String json) throws Exception {
        // Given
        when(ordemServicoService.buscarPorId(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(ordemServicoService.atualizar(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(ordemServicoService.atualizarParcial(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(relatorioOrdemServicoService.gerar(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        doThrow(new OrdemServicoNaoEncontradoException(99L)).when(ordemServicoService).deletar(99L);

        // When
        ResultActions resposta = executar(verbo, url, json);

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void mudarDataDeOsComTransfersRetorna400() throws Exception {
        // Given
        when(ordemServicoService.atualizarParcial(eq(1L), any()))
                .thenThrow(new RegraNegocioException("A ordem de serviço #1 tem 2 transfer(s) do dia dela. Desvincule-os antes de mudar a data."));

        // When
        ResultActions resposta = enviar(patch("/api/ordens-servico/1"), "{\"dataServico\":\"2026-10-22\"}");

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "Desvincule"));
    }

    @Test
    void edicaoComVersaoVelhaRetorna409() throws Exception {
        // Given
        when(ordemServicoService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Ordem de serviço", 1L));

        // When
        ResultActions resposta = enviar(put("/api/ordens-servico/1"), VALIDO);

        // Then
        resposta.andExpect(erro(409, "Conflito de edição", "Ordem de serviço 1"));
    }

    @Test
    void excluirOsComTransfersRetorna409() throws Exception {
        // Given
        doThrow(new DataIntegrityViolationException("fk")).when(ordemServicoService).deletar(1L);

        // When
        ResultActions resposta = chamar(delete("/api/ordens-servico/1"));

        // Then
        resposta.andExpect(erro(409, "Conflito de dados", "em uso"));
    }
}
