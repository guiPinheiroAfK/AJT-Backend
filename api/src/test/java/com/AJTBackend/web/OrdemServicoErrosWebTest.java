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
        esperaValidacao(enviar(post("/api/ordens-servico"), "{}"), "dataServico: Data do serviço é obrigatória");
        esperaValidacao(enviar(put("/api/ordens-servico/1"), "{\"status\":\"ABERTA\"}"), "dataServico: Data do serviço é obrigatória");
        esperaErro(enviar(post("/api/ordens-servico"), "{\"dataServico\":\"21/10/2026\"}"), 400, "Requisição inválida", "corpo da requisição");
        verify(ordemServicoService, never()).criar(any());
    }

    @Test
    void statusForaDoEnumNoCorpoENaUrlRetorna400() throws Exception {
        esperaValidacao(enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"status\":\"PAUSADA\"}"),
                "status: valor 'PAUSADA' inválido. Aceitos: ABERTA, EM_ANDAMENTO, FINALIZADA, CANCELADA");
        esperaErro(chamar(get("/api/ordens-servico/buscar").param("status", "PAUSADA")), 400, "Parâmetro inválido", "status");
        esperaErro(chamar(get("/api/ordens-servico/buscar")), 400, "Parâmetro inválido", "'status' é obrigatório");
    }

    @Test
    void filtroComDataInvalidaOuPeriodoInvertidoRetorna400() throws Exception {
        when(ordemServicoService.listar(any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial (2026-10-30) é depois da final (2026-10-01)."));

        esperaErro(chamar(get("/api/ordens-servico").param("dataInicio", "2026-10-30").param("dataFim", "2026-10-01")),
                400, "Regra de negócio", "depois da final");
        esperaValidacao(chamar(get("/api/ordens-servico").param("dataInicio", "ontem")), "dataInicio");
        esperaValidacao(chamar(get("/api/ordens-servico").param("motoristaId", "um")), "motoristaId");
    }

    @Test
    void minhasComDataInvalidaRetorna400() throws Exception {
        esperaErro(mockMvc.perform(get("/api/ordens-servico/minhas").param("data", "hoje")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("mot").roles("MOTORISTA"))),
                400, "Parâmetro inválido", "data");
    }

    @Test
    void motoristaOuVeiculoInexistenteRetorna404() throws Exception {
        when(ordemServicoService.criar(any()))
                .thenThrow(new MotoristaNaoEncontradoException(5L))
                .thenThrow(new VeiculoNaoEncontradoException(6L));

        esperaErro(enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"motoristaId\":5}"), 404, "Recurso não encontrado", "5");
        esperaErro(enviar(post("/api/ordens-servico"), "{\"dataServico\":\"2026-10-21\",\"veiculoId\":6}"), 404, "Recurso não encontrado", "6");
    }

    @Test
    void idInexistenteRetorna404InclusiveNoPdf() throws Exception {
        when(ordemServicoService.buscarPorId(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(ordemServicoService.atualizar(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(ordemServicoService.atualizarParcial(eq(99L), any())).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        when(relatorioOrdemServicoService.gerar(99L)).thenThrow(new OrdemServicoNaoEncontradoException(99L));
        doThrow(new OrdemServicoNaoEncontradoException(99L)).when(ordemServicoService).deletar(99L);

        esperaErro(chamar(get("/api/ordens-servico/99")), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(put("/api/ordens-servico/99"), VALIDO), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(patch("/api/ordens-servico/99"), "{\"status\":\"CANCELADA\"}"), 404, "Recurso não encontrado", "99");
        esperaErro(chamar(get("/api/ordens-servico/99/pdf")), 404, "Recurso não encontrado", "99");
        esperaErro(chamar(delete("/api/ordens-servico/99")), 404, "Recurso não encontrado", "99");
    }

    @Test
    void mudarDataDeOsComTransfersRetorna400() throws Exception {
        when(ordemServicoService.atualizarParcial(eq(1L), any()))
                .thenThrow(new RegraNegocioException("A ordem de serviço #1 tem 2 transfer(s) do dia dela. Desvincule-os antes de mudar a data."));

        esperaErro(enviar(patch("/api/ordens-servico/1"), "{\"dataServico\":\"2026-10-22\"}"), 400, "Regra de negócio", "Desvincule");
    }

    @Test
    void versaoVelhaEOsComTransfersNaExclusaoRetornam409() throws Exception {
        when(ordemServicoService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Ordem de serviço", 1L));
        doThrow(new DataIntegrityViolationException("fk")).when(ordemServicoService).deletar(1L);

        esperaErro(enviar(put("/api/ordens-servico/1"), VALIDO), 409, "Conflito de edição", "Ordem de serviço 1");
        esperaErro(chamar(delete("/api/ordens-servico/1")), 409, "Conflito de dados", "em uso");
    }
}
