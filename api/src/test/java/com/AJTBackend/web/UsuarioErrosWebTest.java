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
        esperaValidacao(enviar(post("/api/usuarios"), json), detalhe);
        verify(usuarioService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        esperaValidacao(enviar(put("/api/usuarios/1"), json), detalhe);
        verify(usuarioService, never()).atualizar(any(), any());
    }

    @Test
    void perfilForaDoEnumListaOsAceitos() throws Exception {
        esperaValidacao(enviar(post("/api/usuarios"), "{\"nome\":\"Maria\",\"username\":\"maria\",\"role\":\"SUPERUSER\"}"),
                "role: valor 'SUPERUSER' inválido. Aceitos: ADMIN, GERENTE, ATENDENTE, MOTORISTA");
    }

    @Test
    void patchRecusaNomeVazioEUsernameForaDoPadrao() throws Exception {
        esperaValidacao(enviar(patch("/api/usuarios/1"), "{\"nome\":\" \"}"), "nome: Nome não pode ser vazio");
        esperaValidacao(enviar(patch("/api/usuarios/1"), "{\"username\":\"a b\"}"), "de 3 a 50 caracteres");
        verify(usuarioService, never()).atualizarParcial(any(), any());
    }

    @Test
    void regrasDoServiceRetornam400() throws Exception {
        when(usuarioService.criar(any())).thenThrow(new RegraNegocioException("Senha é obrigatória no cadastro"));
        when(usuarioService.atualizarParcial(eq(1L), any())).thenThrow(new RegraNegocioException("Você não pode alterar o próprio perfil"));
        doThrow(new RegraNegocioException("Você não pode excluir o próprio usuário")).when(usuarioService).deletar(1L);

        esperaErro(enviar(post("/api/usuarios"), "{\"nome\":\"Maria\",\"username\":\"maria\",\"role\":\"ATENDENTE\"}"),
                400, "Regra de negócio", "Senha é obrigatória");
        esperaErro(enviar(patch("/api/usuarios/1"), "{\"role\":\"ATENDENTE\"}"), 400, "Regra de negócio", "próprio perfil");
        esperaErro(chamar(delete("/api/usuarios/1")), 400, "Regra de negócio", "próprio usuário");
    }

    @Test
    void vinculoComMotoristaInvalidoRetorna400Ou404() throws Exception {
        when(usuarioService.criar(any()))
                .thenThrow(new RegraNegocioException("Só usuário com perfil Motorista pode ser ligado a um cadastro de motorista"))
                .thenThrow(new MotoristaNaoEncontradoException(77L));

        String atendenteComMotorista = "{\"nome\":\"Ana\",\"username\":\"ana\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\",\"motoristaId\":5}";
        esperaErro(enviar(post("/api/usuarios"), atendenteComMotorista), 400, "Regra de negócio", "perfil Motorista");

        String motoristaInexistente = "{\"nome\":\"Ana\",\"username\":\"ana\",\"senha\":\"senha12345\",\"role\":\"MOTORISTA\",\"motoristaId\":77}";
        esperaErro(enviar(post("/api/usuarios"), motoristaInexistente), 404, "Recurso não encontrado", "77");
    }

    @Test
    void usernameDuplicadoRetorna400() throws Exception {
        when(usuarioService.criar(any())).thenThrow(new UsernameJaCadastradoException("maria"));

        esperaErro(enviar(post("/api/usuarios"), VALIDO), 400, "Erro de validação", "Já existe usuário cadastrado com username");
    }

    @Test
    void idEUsernameInexistentesRetornam404() throws Exception {
        when(usuarioService.buscarPorId(99L)).thenThrow(new UsuarioNaoEncontradoException(99L));
        when(usuarioService.atualizar(eq(99L), any())).thenThrow(new UsuarioNaoEncontradoException(99L));
        when(usuarioService.buscarPorUsername("ninguem")).thenThrow(new UsuarioNaoEncontradoException("ninguem"));
        doThrow(new UsuarioNaoEncontradoException(99L)).when(usuarioService).deletar(99L);

        esperaErro(chamar(get("/api/usuarios/99")), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(put("/api/usuarios/99"), VALIDO), 404, "Recurso não encontrado", "99");
        esperaErro(chamar(delete("/api/usuarios/99")), 404, "Recurso não encontrado", "99");
        esperaErro(chamar(get("/api/usuarios/buscar").param("username", "ninguem")), 404, "Recurso não encontrado", "ninguem");
        esperaErro(chamar(get("/api/usuarios/buscar")), 400, "Parâmetro inválido", "'username' é obrigatório");
    }

    @Test
    void versaoVelhaRetorna409() throws Exception {
        when(usuarioService.atualizar(eq(1L), any())).thenThrow(new ConflitoDeVersaoException("Usuário", 1L));

        esperaErro(enviar(put("/api/usuarios/1"), VALIDO), 409, "Conflito de edição", "Usuário 1");
    }
}
