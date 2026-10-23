package com.AJTBackend.web;

import com.AJTBackend.dto.FiltroMotoristaDTO;
import com.AJTBackend.dto.FiltroPassageiroDTO;
import com.AJTBackend.exception.CredenciaisInvalidasException;
import com.AJTBackend.exception.MuitasTentativasException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.model.Motorista;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
 * por que existe: cada erro precisa sair no mesmo formato (ErroResponseDTO) e com o status
 * certo — inclusive os que ninguem previu, que nao podem mostrar stack trace pro usuario.
 */
class TratamentoGlobalErrosWebTest extends ErrosWebTestBase {

    @Test
    void falhaInesperadaRetorna500SemDetalheInterno() throws Exception {
        // Given
        when(passageiroService.listar(any(FiltroPassageiroDTO.class), any()))
                .thenThrow(new IllegalStateException("connection pool esgotado em db-01:5432"));

        // When
        ResultActions resposta = chamar(get("/api/passageiros"));

        // Then
        resposta.andExpect(erro(500, "Erro interno", "erro inesperado"))
                .andExpect(jsonPath("$.mensagem", not(containsString("db-01")))); // nada de detalhe interno pro cliente
    }

    @Test
    void gravacaoSimultaneaBarradaPeloVersionRetorna409() throws Exception {
        // Given
        when(veiculoService.atualizarParcial(eq(1L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("Veiculo", 1L));

        // When
        ResultActions resposta = enviar(patch("/api/veiculos/1"), "{\"marca\":\"Fiat\"}");

        // Then
        resposta.andExpect(erro(409, "Conflito de edição", "ao mesmo tempo"));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        // Given
        when(authService.login(any(), anyString())).thenThrow(new CredenciaisInvalidasException());

        // When
        ResultActions resposta = login("{\"username\":\"maria\",\"senha\":\"errada123\"}");

        // Then
        resposta.andExpect(erro(401, "Falha na autenticação", "inválidos"));
    }

    @Test
    void loginBloqueadoPorTentativasRetorna429() throws Exception {
        // Given
        when(authService.login(any(), anyString())).thenThrow(new MuitasTentativasException());

        // When
        ResultActions resposta = login("{\"username\":\"maria\",\"senha\":\"errada123\"}");

        // Then
        resposta.andExpect(erro(429, "Muitas tentativas", "Aguarde"));
    }

    @Test
    void loginComUsernameGrandeDemaisRetorna400() throws Exception {
        // Given
        String corpo = "{\"username\":\"" + "u".repeat(51) + "\",\"senha\":\"x\"}";

        // When
        ResultActions resposta = login(corpo);

        // Then
        resposta.andExpect(validacao("username: Username deve ter no máximo 50 caracteres"));
        verify(authService, never()).login(any(), anyString());
    }

    @Test
    void loginComSenhaGrandeDemaisRetorna400() throws Exception {
        // Given
        String corpo = "{\"username\":\"maria\",\"senha\":\"" + "s".repeat(73) + "\"}";

        // When
        ResultActions resposta = login(corpo);

        // Then
        resposta.andExpect(validacao("senha: Senha deve ter no máximo 72 caracteres"));
        verify(authService, never()).login(any(), anyString());
    }

    private ResultActions login(String corpo) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    static Stream<Arguments> trocasDeSenhaInvalidas() {
        return Stream.of(
                Arguments.of("{\"novaSenha\":\"novasenha1\"}", "senhaAtual: Senha atual é obrigatória"),
                Arguments.of("{\"senhaAtual\":\"atual1234\",\"novaSenha\":\"curta\"}",
                        "novaSenha: Nova senha deve ter entre 8 e 72 caracteres"));
    }

    @ParameterizedTest(name = "PUT /api/auth/senha {0} -> 400")
    @MethodSource("trocasDeSenhaInvalidas")
    void trocaDeSenhaComCampoInvalidoRetorna400(String corpo, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(put("/api/auth/senha"), corpo);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(authService, never()).trocarSenha(anyString(), any());
    }

    @Test
    void trocaDeSenhaComSenhaAtualErradaRetorna400() throws Exception {
        // Given
        when(authService.trocarSenha(anyString(), any())).thenThrow(new RegraNegocioException("Senha atual incorreta"));

        // When
        ResultActions resposta = enviar(put("/api/auth/senha"), "{\"senhaAtual\":\"errada123\",\"novaSenha\":\"novasenha1\"}");

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "Senha atual incorreta"));
    }

    @Test
    void dataDoPainelForaDoFormatoRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/painel/resumo").param("data", "19-10-2026"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "data"));
    }

    @Test
    void registroIdDaAuditoriaQueNaoEhNumeroRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/auditoria").param("tabela", "transfers").param("registroId", "sete"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "registroId"));
    }

    @Test
    void cotacaoSemMoedaDeDestinoRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/cotacao").param("de", "USD"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'para' é obrigatório"));
        verifyNoInteractions(cotacaoService);
    }

    @Test
    void cotacaoComCodigoDeMoedaInvalidoRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/cotacao").param("de", "US").param("para", "BRL"));

        // Then
        resposta.andExpect(erro(400, "Erro de validação", "parâmetros"));
        verifyNoInteractions(cotacaoService);
    }

    @Test
    void putNoPainelRetorna405EmJson() throws Exception {
        // When
        ResultActions resposta = chamar(put("/api/painel/resumo"));

        // Then
        resposta.andExpect(erro(405, "Método não permitido", "PUT"));
    }

    @Test
    void postNaCotacaoRetorna405EmJson() throws Exception {
        // When
        ResultActions resposta = mockMvc.perform(post("/api/cotacao").with(user("t").roles("ATENDENTE")));

        // Then
        resposta.andExpect(erro(405, "Método não permitido", "POST"));
    }

    @Test
    void ordenacaoPorCampoInexistenteRetorna400ComONomeDoCampo() throws Exception {
        // Given
        when(motoristaService.listar(any(FiltroMotoristaDTO.class), any()))
                .thenThrow(new PropertyReferenceException("xpto", TypeInformation.of(Motorista.class), List.of()));

        // When
        ResultActions resposta = chamar(get("/api/motoristas").param("sort", "xpto,asc"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'xpto'"));
    }

    @Test
    void numeroComTextoNoCorpoRetorna400DeCorpoMalFormatado() throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/veiculos"), "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":\"muitos\"}");

        // Then
        resposta.andExpect(erro(400, "Requisição inválida", "mal formatado"));
    }

    @Test
    void rotaInexistenteRetorna404EmJson() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/nao-existe"));

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "nao-existe"));
    }
}
