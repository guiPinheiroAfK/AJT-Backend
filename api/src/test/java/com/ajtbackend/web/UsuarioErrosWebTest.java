package com.ajtbackend.web;

import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.MotoristaNaoEncontradoException;
import com.ajtbackend.exception.RegraNegocioException;
import com.ajtbackend.exception.UsernameJaCadastradoException;
import com.ajtbackend.exception.UsuarioNaoEncontradoException;
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
    void deveRetornar400SemChamarOService_quandoCadastroTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/usuarios"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(usuarioService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void deveRetornar400SemChamarOService_quandoPutTemCorpoInvalido(String caso, String json, String detalhe) throws Exception {
        // when
        ResultActions resposta = enviar(put("/api/usuarios/1"), json);

        // then
        resposta.andExpect(validacao(detalhe));
        verify(usuarioService, never()).atualizar(any(), any());
    }

    @Test
    void deveRetornar400ListandoOsAceitos_quandoPerfilNaoExiste() throws Exception {
        // when
        ResultActions resposta = enviar(post("/api/usuarios"), "{\"nome\":\"Maria\",\"username\":\"maria\",\"role\":\"SUPERUSER\"}");

        // then
        resposta.andExpect(validacao("role: valor 'SUPERUSER' inválido. Aceitos: ADMIN, GERENTE, ATENDENTE, MOTORISTA"));
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPatchTrazNomeEmBranco() throws Exception {
        // when
        ResultActions resposta = enviar(patch("/api/usuarios/1"), "{\"nome\":\" \"}");

        // then
        resposta.andExpect(validacao("nome: Nome não pode ser vazio"));
        verify(usuarioService, never()).atualizarParcial(any(), any());
    }

    @Test
    void deveRetornar400SemChamarOService_quandoPatchTrazUsernameForaDoPadrao() throws Exception {
        // when
        ResultActions resposta = enviar(patch("/api/usuarios/1"), "{\"username\":\"a b\"}");

        // then
        resposta.andExpect(validacao("de 3 a 50 caracteres"));
        verify(usuarioService, never()).atualizarParcial(any(), any());
    }

    // ----- regras do service -----

    @Test
    void deveRetornar400_quandoCadastroVemSemSenha() throws Exception {
        // given
        when(usuarioService.criar(any())).thenThrow(new RegraNegocioException("Senha é obrigatória no cadastro"));

        // when
        ResultActions resposta = enviar(post("/api/usuarios"), "{\"nome\":\"Maria\",\"username\":\"maria\",\"role\":\"ATENDENTE\"}");

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "Senha é obrigatória"));
    }

    @Test
    void deveRetornar400_quandoUsuarioTentaAlterarOProprioPerfil() throws Exception {
        // given
        when(usuarioService.atualizarParcial(eq(1L), any())).thenThrow(new RegraNegocioException("Você não pode alterar o próprio perfil"));

        // when
        ResultActions resposta = enviar(patch("/api/usuarios/1"), "{\"role\":\"ATENDENTE\"}");

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "próprio perfil"));
    }

    @Test
    void deveRetornar400_quandoUsuarioTentaSeExcluir() throws Exception {
        // given
        doThrow(new RegraNegocioException("Você não pode excluir o próprio usuário")).when(usuarioService).deletar(1L);

        // when
        ResultActions resposta = chamar(delete("/api/usuarios/1"));

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "próprio usuário"));
    }

    @Test
    void deveRetornar400_quandoPerfilNaoMotoristaTentaSeLigarAMotorista() throws Exception {
        // given
        when(usuarioService.criar(any()))
                .thenThrow(new RegraNegocioException("Só usuário com perfil Motorista pode ser ligado a um cadastro de motorista"));
        String atendenteComMotorista = "{\"nome\":\"Ana\",\"username\":\"ana\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\",\"motoristaId\":5}";

        // when
        ResultActions resposta = enviar(post("/api/usuarios"), atendenteComMotorista);

        // then
        resposta.andExpect(erro(400, "Regra de negócio", "perfil Motorista"));
    }

    @Test
    void deveRetornar404_quandoLigaAMotoristaInexistente() throws Exception {
        // given
        when(usuarioService.criar(any())).thenThrow(new MotoristaNaoEncontradoException(77L));
        String motoristaInexistente = "{\"nome\":\"Ana\",\"username\":\"ana\",\"senha\":\"senha12345\",\"role\":\"MOTORISTA\",\"motoristaId\":77}";

        // when
        ResultActions resposta = enviar(post("/api/usuarios"), motoristaInexistente);

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "77"));
    }

    @Test
    void deveRetornar400_quandoUsernameJaEstaCadastrado() throws Exception {
        // given
        when(usuarioService.criar(any())).thenThrow(new UsernameJaCadastradoException("maria"));

        // when
        ResultActions resposta = enviar(post("/api/usuarios"), VALIDO);

        // then
        resposta.andExpect(erro(400, "Erro de validação", "Já existe usuário cadastrado com username"));
    }

    // ----- inexistentes -----

    @Test
    void deveRetornar404_quandoGetEmIdInexistente() throws Exception {
        // given
        when(usuarioService.buscarPorId(99L)).thenThrow(new UsuarioNaoEncontradoException(99L));

        // when
        ResultActions resposta = chamar(get("/api/usuarios/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoPutEmIdInexistente() throws Exception {
        // given
        when(usuarioService.atualizar(eq(99L), any())).thenThrow(new UsuarioNaoEncontradoException(99L));

        // when
        ResultActions resposta = enviar(put("/api/usuarios/99"), VALIDO);

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoDeleteEmIdInexistente() throws Exception {
        // given
        doThrow(new UsuarioNaoEncontradoException(99L)).when(usuarioService).deletar(99L);

        // when
        ResultActions resposta = chamar(delete("/api/usuarios/99"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "99"));
    }

    @Test
    void deveRetornar404_quandoBuscaUsernameInexistente() throws Exception {
        // given
        when(usuarioService.buscarPorUsername("ninguem")).thenThrow(new UsuarioNaoEncontradoException("ninguem"));

        // when
        ResultActions resposta = chamar(get("/api/usuarios/buscar").param("username", "ninguem"));

        // then
        resposta.andExpect(erro(404, "Recurso não encontrado", "ninguem"));
    }

    @Test
    void deveRetornar400_quandoBuscaPorUsernameSemOParametro() throws Exception {
        // when
        ResultActions resposta = chamar(get("/api/usuarios/buscar"));

        // then
        resposta.andExpect(erro(400, "Parâmetro inválido", "'username' é obrigatório"));
    }

    @Test
    void deveRetornar409_quandoPutTrazVersaoVelha() throws Exception {
        // given
        when(usuarioService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Usuário", 1L));

        // when
        ResultActions resposta = enviar(put("/api/usuarios/1"), VALIDO);

        // then
        resposta.andExpect(erro(409, "Conflito de edição", "Usuário 1"));
    }
}
