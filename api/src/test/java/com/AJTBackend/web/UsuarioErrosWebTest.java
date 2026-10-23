package com.AJTBackend.web;

import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.UsernameJaCadastradoException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
 * o que testa: os caminhos de erro de /api/usuarios — regras do UsuarioRequestDTO
 * (nome, formato do username, tamanho da senha, perfil ausente ou fora do enum), as
 * regras do service (senha obrigatoria no cadastro, nao excluir/rebaixar a si mesmo,
 * vinculo com motorista so pro perfil Motorista), username duplicado, id/username
 * inexistentes e versao velha.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — cada erro com teste proprio e o status HTTP certo.
 */
class UsuarioErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"nome\":\"Maria\",\"username\":\"maria\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\"}";

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem nome", "{\"username\":\"maria\",\"role\":\"ATENDENTE\"}", "nome: Nome é obrigatório"),
                Arguments.of("sem username", "{\"nome\":\"Maria\",\"role\":\"ATENDENTE\"}", "username: Username é obrigatório"),
                Arguments.of("username curto", "{\"nome\":\"Maria\",\"username\":\"ma\",\"role\":\"ATENDENTE\"}", "de 3 a 50 caracteres"),
                Arguments.of("username com espaco", "{\"nome\":\"Maria\",\"username\":\"maria souza\",\"role\":\"ATENDENTE\"}", "de 3 a 50 caracteres"),
                Arguments.of("username com acento", "{\"nome\":\"Maria\",\"username\":\"mária\",\"role\":\"ATENDENTE\"}", "de 3 a 50 caracteres"),
                Arguments.of("senha curta", "{\"nome\":\"Maria\",\"username\":\"maria\",\"senha\":\"1234567\",\"role\":\"ATENDENTE\"}", "entre 8 e 72 caracteres"),
                Arguments.of("senha maior que 72", "{\"nome\":\"Maria\",\"username\":\"maria\",\"senha\":\"" + "s".repeat(73) + "\",\"role\":\"ATENDENTE\"}", "entre 8 e 72 caracteres"),
                Arguments.of("sem perfil", "{\"nome\":\"Maria\",\"username\":\"maria\"}", "role: Role é obrigatória"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/usuarios"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(usuarioService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(put("/api/usuarios/1"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(usuarioService, never()).atualizar(any(), any());
    }

    @Test
    void perfilForaDoEnumListaOsAceitos() throws Exception {
        // When
        ResultActions resposta = enviar(post("/api/usuarios"), "{\"nome\":\"Maria\",\"username\":\"maria\",\"role\":\"SUPERUSER\"}");

        // Then
        resposta.andExpect(validacao("role: valor 'SUPERUSER' inválido. Aceitos: ADMIN, GERENTE, ATENDENTE, MOTORISTA"));
    }

    static Stream<Arguments> patchesInvalidos() {
        return Stream.of(
                Arguments.of("{\"nome\":\" \"}", "nome: Nome não pode ser vazio"),
                Arguments.of("{\"username\":\"a b\"}", "de 3 a 50 caracteres"));
    }

    @ParameterizedTest(name = "PATCH {0} -> 400")
    @MethodSource("patchesInvalidos")
    void patchComNomeVazioOuUsernameForaDoPadraoRetorna400(String json, String detalhe) throws Exception {
        // When
        ResultActions resposta = enviar(patch("/api/usuarios/1"), json);

        // Then
        resposta.andExpect(validacao(detalhe));
        verify(usuarioService, never()).atualizarParcial(any(), any());
    }

    @Test
    void cadastroSemSenhaRetorna400() throws Exception {
        // Given
        when(usuarioService.criar(any())).thenThrow(new RegraNegocioException("Senha é obrigatória no cadastro"));

        // When
        ResultActions resposta = enviar(post("/api/usuarios"), "{\"nome\":\"Maria\",\"username\":\"maria\",\"role\":\"ATENDENTE\"}");

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "Senha é obrigatória"));
    }

    @Test
    void alterarOProprioPerfilRetorna400() throws Exception {
        // Given
        when(usuarioService.atualizarParcial(eq(1L), any())).thenThrow(new RegraNegocioException("Você não pode alterar o próprio perfil"));

        // When
        ResultActions resposta = enviar(patch("/api/usuarios/1"), "{\"role\":\"ATENDENTE\"}");

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "próprio perfil"));
    }

    @Test
    void excluirOProprioUsuarioRetorna400() throws Exception {
        // Given
        doThrow(new RegraNegocioException("Você não pode excluir o próprio usuário")).when(usuarioService).deletar(1L);

        // When
        ResultActions resposta = chamar(delete("/api/usuarios/1"));

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "próprio usuário"));
    }

    @Test
    void atendenteLigadoAMotoristaRetorna400() throws Exception {
        // Given
        when(usuarioService.criar(any())).thenThrow(
                new RegraNegocioException("Só usuário com perfil Motorista pode ser ligado a um cadastro de motorista"));
        String atendenteComMotorista = "{\"nome\":\"Ana\",\"username\":\"ana\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\",\"motoristaId\":5}";

        // When
        ResultActions resposta = enviar(post("/api/usuarios"), atendenteComMotorista);

        // Then
        resposta.andExpect(erro(400, "Regra de negócio", "perfil Motorista"));
    }

    @Test
    void ligarAMotoristaInexistenteRetorna404() throws Exception {
        // Given
        when(usuarioService.criar(any())).thenThrow(new MotoristaNaoEncontradoException(77L));
        String motoristaInexistente = "{\"nome\":\"Ana\",\"username\":\"ana\",\"senha\":\"senha12345\",\"role\":\"MOTORISTA\",\"motoristaId\":77}";

        // When
        ResultActions resposta = enviar(post("/api/usuarios"), motoristaInexistente);

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "77"));
    }

    @Test
    void usernameDuplicadoRetorna400() throws Exception {
        // Given
        when(usuarioService.criar(any())).thenThrow(new UsernameJaCadastradoException("maria"));

        // When
        ResultActions resposta = enviar(post("/api/usuarios"), VALIDO);

        // Then
        resposta.andExpect(erro(400, "Erro de validação", "Já existe usuário cadastrado com username"));
    }

    static Stream<Arguments> requisicoesAoUsuario99() {
        return Stream.of(
                Arguments.of("GET", null),
                Arguments.of("PUT", VALIDO),
                Arguments.of("DELETE", null));
    }

    @ParameterizedTest(name = "{0} /api/usuarios/99 -> 404")
    @MethodSource("requisicoesAoUsuario99")
    void idInexistenteRetorna404EmCadaVerbo(String verbo, String json) throws Exception {
        // Given
        when(usuarioService.buscarPorId(99L)).thenThrow(new UsuarioNaoEncontradoException(99L));
        when(usuarioService.atualizar(eq(99L), any())).thenThrow(new UsuarioNaoEncontradoException(99L));
        doThrow(new UsuarioNaoEncontradoException(99L)).when(usuarioService).deletar(99L);

        // When
        ResultActions resposta = executar(verbo, "/api/usuarios/99", json);

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void buscaPorUsernameInexistenteRetorna404() throws Exception {
        // Given
        when(usuarioService.buscarPorUsername("ninguem")).thenThrow(new UsuarioNaoEncontradoException("ninguem"));

        // When
        ResultActions resposta = chamar(get("/api/usuarios/buscar").param("username", "ninguem"));

        // Then
        resposta.andExpect(erro(404, "Recurso não encontrado", "ninguem"));
    }

    @Test
    void buscaPorUsernameSemOParametroRetorna400() throws Exception {
        // When
        ResultActions resposta = chamar(get("/api/usuarios/buscar"));

        // Then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'username' é obrigatório"));
        verify(usuarioService, never()).buscarPorUsername(any());
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        // Given
        when(usuarioService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Usuário", 1L));

        // When
        ResultActions resposta = enviar(put("/api/usuarios/1"), VALIDO);

        // Then
        resposta.andExpect(erro(409, "Conflito de edição", "Usuário 1"));
    }
}
