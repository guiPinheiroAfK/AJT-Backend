package com.ajtbackend.web;

import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.MotoristaNaoEncontradoException;
import com.ajtbackend.exception.OrdemServicoNaoEncontradoException;
import com.ajtbackend.exception.RegraNegocioException;
import com.ajtbackend.exception.VeiculoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.ResultActions;

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

    // ----- corpo e parametros -----

    @Test
    void deveRetornar400SemChamarOService_quandoCadastroNaoTemData() throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{}");

        // then
        resposta.andExpect(validacao("dataServico: Data do serviço é obrigatória"));
        verify(ordemServicoService, never()).criar(any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPutNaoTemData() throws Exception {
        // when
        ResultActions resposta = enviar(put("/api/ordens-servico/1"), "{\"status\":\"ABERTA\"}");

        // then
        resposta.andExpect(validacao("dataServico: Data do serviço é obrigatória"));
        verify(ordemServicoService, never()).atualizar(any(), any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoDataNaoEstaNoFormatoIso() throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"21/10/2026\"}");

        // then
        resposta.andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
        verify(ordemServicoService, never()).criar(any());
    }

    @Test
    void deveRetornar400ListandoOsAceitos_quandoStatusDoCorpoNaoExiste() throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"status\":\"PAUSADA\"}");

        // then
        resposta.andExpect(validacao("status: valor 'PAUSADA' inválido. Aceitos: ABERTA, EM_ANDAMENTO, FINALIZADA, CANCELADA"));
    }

    @Test
    void deveRetornar400_quandoStatusDaUrlNaoExiste() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/ordens-servico/buscar").param("status", "PAUSADA"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "status"));
    }

    @Test
    void deveRetornar400_quandoBuscaPorStatusSemOParametro() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/ordens-servico/buscar"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'status' é obrigatório"));
    }

    @Test
    void deveRetornar400_quandoPeriodoDoFiltroEstaInvertido() throws Exception {
        // given
        when(ordemServicoService.listar(any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial (2026-10-30) é depois da final (2026-10-01)."));

        // when
        ResultActions resposta = chamar(get("/api/ordens-servico").param("dataInicio", "2026-10-30").param("dataFim", "2026-10-01"));

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "depois da final"));
    }

    @Test
    void deveRetornar400_quandoDataDoFiltroEhInvalida() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/ordens-servico").param("dataInicio", "ontem"));

        // then
        resposta.andExpect(validacao("dataInicio"));
    }

    @Test
    void deveRetornar400_quandoMotoristaIdDoFiltroNaoEhNumero() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/ordens-servico").param("motoristaId", "um"));

        // then
        resposta.andExpect(validacao("motoristaId"));
    }

    @Test
    void deveRetornar400_quandoMinhasRecebeDataInvalida() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(get("/api/ordens-servico/minhas").param("data", "hoje")
                .with(user("mot").roles("MOTORISTA")));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "data"));
    }

    // ----- inexistentes -----

    @Test
    void deveRetornar404_quandoCadastroApontaMotoristaInexistente() throws Exception {
        // given
        when(ordemServicoService.criar(any())).thenThrow(new MotoristaNaoEncontradoException(5L));

        // when
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"motoristaId\":5}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "5"));
    }

    @Test
    void deveRetornar404_quandoCadastroApontaVeiculoInexistente() throws Exception {
        // given
        when(ordemServicoService.criar(any())).thenThrow(new VeiculoNaoEncontradoException(6L));

        // when
        ResultActions resposta = enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"veiculoId\":6}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "6"));
    }

    @Test
    void deveRetornar404_quandoGetEmIdInexistente() throws Exception {
        // given
        when(ordemServicoService.buscarPorId(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));

        // when
        ResultActions resposta = chamar(get("/api/ordens-servico/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPutEmIdInexistente() throws Exception {
        // given
        when(ordemServicoService.atualizar(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(put("/api/ordens-servico/99"), VALIDO);

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPatchEmIdInexistente() throws Exception {
        // given
        when(ordemServicoService.atualizarParcial(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(patch("/api/ordens-servico/99"), "{\"status\":\"CANCELADA\"}");

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPdfDeOsInexistente() throws Exception {
        // given
        when(relatorioOrdemServicoService.gerar(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));

        // when
        ResultActions resposta = chamar(get("/api/ordens-servico/99/pdf"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoDeleteEmIdInexistente() throws Exception {
        // given
        doThrow(new OrdemServicoNaoEncontradoException(99L)).when(ordemServicoService).deletar(99L);

        // when
        ResultActions resposta = chamar(delete("/api/ordens-servico/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    // ----- regra de negocio e conflito -----

    @Test
    void deveRetornar400_quandoMudaADataDeOsComTransfers() throws Exception {
        // given
        when(ordemServicoService.atualizarParcial(eq(1L), any()))
                .thenThrow(new RegraNegocioException("A ordem de serviço #1 tem 2 transfer(s) do dia dela. Desvincule-os antes de mudar a data."));

        // when
        ResultActions resposta = enviar(patch("/api/ordens-servico/1"), "{\"dataServico\":\"2026-10-22\"}");

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "Desvincule"));
    }

    @Test
    void deveRetornar409_quandoPutTrazVersaoVelha() throws Exception {
        // given
        when(ordemServicoService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Ordem de serviço", 1L));

        // when
        ResultActions resposta = enviar(put("/api/ordens-servico/1"), VALIDO);

        // then
        resposta.andExpect(erro(409, "Conflito de edição", "Ordem de serviço 1"));
    }

    @Test
    void deveRetornar409_quandoExcluiOsComTransfers() throws Exception {
        // given
        doThrow(new DataIntegrityViolationException("fk")).when(ordemServicoService).deletar(1L);

        // when
        ResultActions resposta = chamar(delete("/api/ordens-servico/1"));

        // then
        resposta.andExpect(erro(409, "Conflito de dados", "em uso"));
    }
}
