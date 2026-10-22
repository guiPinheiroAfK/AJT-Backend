package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

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
    void dataObrigatoriaEmFormatoValido() throws Exception {
        enviar(post("/api/ordens-servico"), "{}").andExpect(validacao("dataServico: Data do serviço é obrigatória"));
        enviar(put("/api/ordens-servico/1"), "{\"status\":\"ABERTA\"}").andExpect(validacao("dataServico: Data do serviço é obrigatória"));
        enviar(post("/api/ordens-servico"), "{\"dataServico\":\"21/10/2026\"}").andExpect(erro(400, "Requisição inválida", "corpo da requisição"));
        verify(ordemServicoService, never()).criar(any());
    }

    @Test
    void statusForaDoEnumNoCorpoENaUrlRetorna400() throws Exception {
        enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"status\":\"PAUSADA\"}")
                .andExpect(validacao("status: valor 'PAUSADA' inválido. Aceitos: ABERTA, EM_ANDAMENTO, FINALIZADA, CANCELADA"));
        chamar(get("/api/ordens-servico/buscar").param("status", "PAUSADA")).andExpect(erro(400, "Parâmetro inválido", "status"));
        chamar(get("/api/ordens-servico/buscar")).andExpect(erro(400, "Parâmetro inválido", "'status' é obrigatório"));
    }

    @Test
    void filtroComDataInvalidaOuPeriodoInvertidoRetorna400() throws Exception {
        when(ordemServicoService.listar(any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial (2026-10-30) é depois da final (2026-10-01)."));

        chamar(get("/api/ordens-servico").param("dataInicio", "2026-10-30").param("dataFim", "2026-10-01"))
                .andExpect(erro(400, "Regra de negócio", "depois da final"));
        chamar(get("/api/ordens-servico").param("dataInicio", "ontem")).andExpect(validacao("dataInicio"));
        chamar(get("/api/ordens-servico").param("motoristaId", "um")).andExpect(validacao("motoristaId"));
    }

    @Test
    void minhasComDataInvalidaRetorna400() throws Exception {
        mockMvc.perform(get("/api/ordens-servico/minhas").param("data", "hoje")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("mot").roles("MOTORISTA")))
                .andExpect(erro(400, "Parâmetro inválido", "data"));
    }

    @Test
    void motoristaOuVeiculoInexistenteRetorna404() throws Exception {
        when(ordemServicoService.criar(any()))
                .thenThrow(new MotoristaNaoEncontradoException(5L))
                .thenThrow(new VeiculoNaoEncontradoException(6L));

        enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"motoristaId\":5}").andExpect(erro(404, "Recurso não encontrado", "5"));
        enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"veiculoId\":6}").andExpect(erro(404, "Recurso não encontrado", "6"));
    }

    @Test
    void idInexistenteRetorna404InclusiveNoPdf() throws Exception {
        when(ordemServicoService.buscarPorId(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(ordemServicoService.atualizar(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(ordemServicoService.atualizarParcial(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(relatorioOrdemServicoService.gerar(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        doThrow(new OrdemServicoNaoEncontradoException(99L)).when(ordemServicoService).deletar(99L);

        chamar(get("/api/ordens-servico/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(put("/api/ordens-servico/99"), VALIDO).andExpect(erro(404, "Recurso não encontrado", "99"));
        enviar(patch("/api/ordens-servico/99"), "{\"status\":\"CANCELADA\"}").andExpect(erro(404, "Recurso não encontrado", "99"));
        chamar(get("/api/ordens-servico/99/pdf")).andExpect(erro(404, "Recurso não encontrado", "99"));
        chamar(delete("/api/ordens-servico/99")).andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void mudarDataDeOsComTransfersRetorna400() throws Exception {
        when(ordemServicoService.atualizarParcial(eq(1L), any()))
                .thenThrow(new RegraNegocioException("A ordem de serviço #1 tem 2 transfer(s) do dia dela. Desvincule-os antes de mudar a data."));

        enviar(patch("/api/ordens-servico/1"), "{\"dataServico\":\"2026-10-22\"}").andExpect(erro(400, "Regra de negócio", "Desvincule"));
    }

    @Test
    void versaoVelhaEOsComTransfersNaExclusaoRetornam409() throws Exception {
        when(ordemServicoService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Ordem de serviço", 1L));
        doThrow(new DataIntegrityViolationException("fk")).when(ordemServicoService).deletar(1L);

        enviar(put("/api/ordens-servico/1"), VALIDO).andExpect(erro(409, "Conflito de edição", "Ordem de serviço 1"));
        chamar(delete("/api/ordens-servico/1")).andExpect(erro(409, "Conflito de dados", "em uso"));
    }
}
