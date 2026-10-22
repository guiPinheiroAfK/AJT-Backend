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

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void falhaInesperadaRetorna500ComMensagemGenerica() throws Exception {
        when(passageiroService.listar(any(FiltroPassageiroDTO.class), any()))
                .thenThrow(new IllegalStateException("connection pool esgotado em db-01:5432"));

        chamar(get("/api/passageiros")).andExpect(erro(500, "Erro interno", "erro inesperado"));
        chamar(get("/api/passageiros"))
                .andExpect(jsonPath("$.mensagem", not(containsString("db-01")))); // nada de detalhe interno pro cliente
    }

    @Test
    void gravacaoSimultaneaBarradaPeloVersionRetorna409() throws Exception {
        when(veiculoService.atualizarParcial(eq(1L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("Veiculo", 1L));

        enviar(patch("/api/veiculos/1"), "{\"marca\":\"Fiat\"}").andExpect(erro(409, "Conflito de edição", "ao mesmo tempo"));
    }

    @Test
    void loginComSenhaErradaRetorna401EBloqueadoRetorna429() throws Exception {
        when(authService.login(any(), anyString()))
                .thenThrow(new CredenciaisInvalidasException())
                .thenThrow(new MuitasTentativasException());

        String corpo = "{\"username\":\"maria\",\"senha\":\"errada123\"}";
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(erro(401, "Falha na autenticação", "inválidos"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(erro(429, "Muitas tentativas", "Aguarde"));
    }

    @Test
    void loginComCamposGrandesDemaisRetorna400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + "u".repeat(51) + "\",\"senha\":\"x\"}"))
                .andExpect(validacao("username: Username deve ter no máximo 50 caracteres"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"maria\",\"senha\":\"" + "s".repeat(73) + "\"}"))
                .andExpect(validacao("senha: Senha deve ter no máximo 72 caracteres"));
    }

    @Test
    void trocaDeSenhaInvalidaRetorna400() throws Exception {
        enviar(put("/api/auth/senha"), "{\"novaSenha\":\"novasenha1\"}").andExpect(validacao("senhaAtual: Senha atual é obrigatória"));
        enviar(put("/api/auth/senha"), "{\"senhaAtual\":\"atual1234\",\"novaSenha\":\"curta\"}")
                .andExpect(validacao("novaSenha: Nova senha deve ter entre 8 e 72 caracteres"));

        when(authService.trocarSenha(anyString(), any())).thenThrow(new RegraNegocioException("Senha atual incorreta"));
        enviar(put("/api/auth/senha"), "{\"senhaAtual\":\"errada123\",\"novaSenha\":\"novasenha1\"}")
                .andExpect(erro(400, "Regra de negócio", "Senha atual incorreta"));
    }

    @Test
    void parametrosComTipoOuFormatoErradoRetornam400() throws Exception {
        chamar(get("/api/painel/resumo").param("data", "19-10-2026")).andExpect(erro(400, "Parâmetro inválido", "data"));
        chamar(get("/api/auditoria").param("tabela", "transfers").param("registroId", "sete"))
                .andExpect(erro(400, "Parâmetro inválido", "registroId"));
        chamar(get("/api/cotacao").param("de", "USD")).andExpect(erro(400, "Parâmetro inválido", "'para' é obrigatório"));
        chamar(get("/api/cotacao").param("de", "US").param("para", "BRL")).andExpect(erro(400, "Erro de validação", "parâmetros"));
    }

    @Test
    void verboNaoSuportadoRetorna405EmJson() throws Exception {
        chamar(put("/api/painel/resumo")).andExpect(erro(405, "Método não permitido", "PUT"));
        mockMvc.perform(post("/api/cotacao").with(user("t").roles("ATENDENTE"))).andExpect(erro(405, "Método não permitido", "POST"));
    }

    @Test
    void ordenacaoPorCampoInexistenteRetorna400ComONomeDoCampo() throws Exception {
        when(motoristaService.listar(any(FiltroMotoristaDTO.class), any()))
                .thenThrow(new PropertyReferenceException("xpto", TypeInformation.of(Motorista.class), List.of()));

        chamar(get("/api/motoristas").param("sort", "xpto,asc")).andExpect(erro(400, "Parâmetro inválido", "'xpto'"));
    }

    @Test
    void numeroComTextoNoCorpoRetorna400DeCorpoMalFormatado() throws Exception {
        enviar(post("/api/veiculos"), "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":\"muitos\"}")
                .andExpect(erro(400, "Requisição inválida", "mal formatado"));
    }

    @Test
    void rotaInexistenteRetorna404EmJson() throws Exception {
        chamar(get("/api/nao-existe")).andExpect(erro(404, "Recurso não encontrado", "nao-existe"));
    }
}
