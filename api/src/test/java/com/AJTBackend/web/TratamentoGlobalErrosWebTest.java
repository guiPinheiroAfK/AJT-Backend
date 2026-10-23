package com.AJTBackend.web;

import com.AJTBackend.dto.FiltroMotoristaDTO;
import com.AJTBackend.dto.FiltroPassageiroDTO;
import com.AJTBackend.exception.CredenciaisInvalidasException;
import com.AJTBackend.exception.MuitasTentativasException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.model.Motorista;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/*
 * o que testa: os erros que nao sao de um recurso so e passam pelo GlobalExceptionHandler —
 * falha inesperada (500 sem vazar detalhe interno), duas gravacoes simultaneas barradas pelo
 * @Version (409), login invalido (401) ou bloqueado (429), troca de senha com dados invalidos,
 * parametros de painel/auditoria/cotacao com tipo ou formato errado, ordenacao por campo
 * inexistente, numero com texto no corpo, verbo nao suportado (405) e rota inexistente (404).
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), services mockados. sem banco. "mvn test".
 *
 * por que existe: todo erro precisa sair no mesmo formato (ErroResponseDTO) e com o status
 * certo — inclusive os que ninguem previu, que nao podem mostrar stack trace pro usuario.
 */
class TratamentoGlobalErrosWebTest extends ErrosWebTestBase {

    private static final String LOGIN_ERRADO = "{\"username\":\"maria\",\"senha\":\"errada123\"}";

    private ResultActions login(String corpo) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    void deveRetornar500SemVazarDetalheInterno_quandoOcorreFalhaInesperada() throws Exception {
        // given
        when(passageiroService.listar(any(FiltroPassageiroDTO.class), any()))
                .thenThrow(new IllegalStateException("connection pool esgotado em db-01:5432"));

        // when
        ResultActions resposta = chamar(get("/api/passageiros"));

        // then
        resposta.andExpect(erro(500, "Erro interno", "erro inesperado"))
                .andExpect(jsonPath("$.mensagem", not(containsString("db-01")))); // nada de detalhe interno pro cliente
    }

    @Test
    void deveRetornar409_quandoGravacaoSimultaneaEhBarradaPeloVersion() throws Exception {
        // given
        when(veiculoService.atualizarParcial(eq(1L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("Veiculo", 1L));

        // when
        ResultActions resposta = enviar(patch("/api/veiculos/1"), "{\"marca\":\"Fiat\"}");

        // then
        resposta.andExpect(erro(409, "Conflito de edição", "ao mesmo tempo"));
    }

    @Test
    void deveRetornar401_quandoLoginTemSenhaErrada() throws Exception {
        // given
        when(authService.login(any(), anyString())).thenThrow(new CredenciaisInvalidasException());

        // when
        ResultActions resposta = login(LOGIN_ERRADO);

        // then
        resposta.andExpect(erro(401, "Falha na autenticação", "inválidos"));
    }

    @Test
    void deveRetornar429_quandoLoginEstaBloqueado() throws Exception {
        // given
        when(authService.login(any(), anyString())).thenThrow(new MuitasTentativasException());

        // when
        ResultActions resposta = login(LOGIN_ERRADO);

        // then
        resposta.andExpect(erro(429, "Muitas tentativas", "Aguarde"));
    }

    @Test
    void deveRetornar400_quandoLoginTemUsernameGrandeDemais() throws Exception {
        // when
        ResultActions resposta = login("{\"username\":\"" + "u".repeat(51) + "\",\"senha\":\"x\"}");

        // then
        resposta.andExpect(validacao("username: Username deve ter no máximo 50 caracteres"));
        verify(authService, never()).login(any(), anyString());
    }

    @Test
    void deveRetornar400_quandoLoginTemSenhaGrandeDemais() throws Exception {
        // when
        ResultActions resposta = login("{\"username\":\"maria\",\"senha\":\"" + "s".repeat(73) + "\"}");

        // then
        resposta.andExpect(validacao("senha: Senha deve ter no máximo 72 caracteres"));
        verify(authService, never()).login(any(), anyString());
    }

    @Test
    void deveRetornar400_quandoTrocaDeSenhaNaoTrazASenhaAtual() throws Exception {
        // when
        ResultActions resposta = enviar(put("/api/auth/senha"), "{\"novaSenha\":\"novasenha1\"}");

        // then
        resposta.andExpect(validacao("senhaAtual: Senha atual é obrigatória"));
        verify(authService, never()).trocarSenha(anyString(), any());
    }

    @Test
    void deveRetornar400_quandoNovaSenhaEhCurta() throws Exception {
        // when
        ResultActions resposta = enviar(put("/api/auth/senha"), "{\"senhaAtual\":\"atual1234\",\"novaSenha\":\"curta\"}");

        // then
        resposta.andExpect(validacao("novaSenha: Nova senha deve ter entre 8 e 72 caracteres"));
        verify(authService, never()).trocarSenha(anyString(), any());
    }

    @Test
    void deveRetornar400DeRegraDeNegocio_quandoSenhaAtualEstaIncorreta() throws Exception {
        // given
        when(authService.trocarSenha(anyString(), any())).thenThrow(new RegraNegocioException("Senha atual incorreta"));

        // when
        ResultActions resposta = enviar(put("/api/auth/senha"), "{\"senhaAtual\":\"errada123\",\"novaSenha\":\"novasenha1\"}");

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "Senha atual incorreta"));
    }

    @Test
    void deveRetornar400_quandoDataDoPainelEstaEmFormatoErrado() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/painel/resumo").param("data", "19-10-2026"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "data"));
    }

    @Test
    void deveRetornar400_quandoRegistroIdDaAuditoriaNaoEhNumero() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/auditoria").param("tabela", "transfers").param("registroId", "sete"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "registroId"));
    }

    @Test
    void deveRetornar400_quandoCotacaoNaoTrazAMoedaDeDestino() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/cotacao").param("de", "USD"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'para' é obrigatório"));
    }

    @Test
    void deveRetornar400_quandoMoedaDaCotacaoNaoTemTresLetras() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/cotacao").param("de", "US").param("para", "BRL"));

        // then
        resposta.andExpect(erro(400, "Erro de validação", "parâmetros"));
    }

    @Test
    void deveRetornar405EmJson_quandoPutNoPainel() throws Exception {
        // when
        ResultActions resposta = chamar(put("/api/painel/resumo"));

        // then
        resposta.andExpect(erro(405, "Método não permitido", "PUT"));
    }

    @Test
    void deveRetornar405EmJson_quandoPostNaCotacao() throws Exception {
        // when
        ResultActions resposta = mockMvc.perform(post("/api/cotacao").with(user("t").roles("ATENDENTE")));

        // then
        resposta.andExpect(erro(405, "Método não permitido", "POST"));
    }

    @Test
    void deveRetornar400ComONomeDoCampo_quandoOrdenaPorCampoInexistente() throws Exception {
        // given
        when(motoristaService.listar(any(FiltroMotoristaDTO.class), any()))
                .thenThrow(new PropertyReferenceException("xpto", TypeInformation.of(Motorista.class), List.of()));

        // when
        ResultActions resposta = chamar(get("/api/motoristas").param("sort", "xpto,asc"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'xpto'"));
    }

    @Test
    void deveRetornar400DeCorpoMalFormatado_quandoNumeroVemComoTexto() throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/veiculos"), "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":\"muitos\"}");

        // then
        resposta.andExpect(erro(400, "Requisição inválida", "mal formatado"));
    }

    @Test
    void deveRetornar404EmJson_quandoRotaNaoExiste() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/nao-existe"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "nao-existe"));
    }
}
