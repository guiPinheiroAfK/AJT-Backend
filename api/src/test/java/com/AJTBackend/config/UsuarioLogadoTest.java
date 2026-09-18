package com.AJTBackend.config;

import com.AJTBackend.model.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: o unico ponto que le o usuario autenticado (UsuarioLogado) —
 * username (ou "sistema" quando ninguem esta logado), comparacao com um
 * username especifico e checagem de perfil.
 *
 * como rodar: teste unitario puro; o "usuario logado" e simulado escrevendo
 * direto no SecurityContextHolder. sem spring, sem banco, sem docker. roda
 * com "mvn test".
 *
 * por que existe: UsuarioService, ParadaOsService e AuditoriaService
 * dependem dessa classe pra regras de seguranca (ninguem se exclui, motorista
 * so muda o status da parada, auditoria diz quem fez). testar aqui uma vez
 * evita repetir a montagem do contexto de seguranca em cada um deles.
 */
class UsuarioLogadoTest {

    private final UsuarioLogado usuarioLogado = new UsuarioLogado();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComo(String username, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, authorities));
    }

    @Test
    void semUsuarioLogadoValeSistema() {
        assertThat(usuarioLogado.usernameOuSistema()).isEqualTo("sistema");
        assertThat(usuarioLogado.ehUsuario("admin")).isFalse();
        assertThat(usuarioLogado.temPerfil(Role.ADMIN)).isFalse();
    }

    @Test
    void devolveOUsernameDoUsuarioLogado() {
        logadoComo("maria", "GERENTE");

        assertThat(usuarioLogado.usernameOuSistema()).isEqualTo("maria");
    }

    @Test
    void ehUsuarioComparaExatamenteOUsername() {
        logadoComo("maria", "GERENTE");

        assertThat(usuarioLogado.ehUsuario("maria")).isTrue();
        assertThat(usuarioLogado.ehUsuario("joao")).isFalse();
        assertThat(usuarioLogado.ehUsuario(null)).isFalse();
    }

    @Test
    void temPerfilConfereAAuthorityComPrefixoRole() {
        logadoComo("carlos", "MOTORISTA");

        assertThat(usuarioLogado.temPerfil(Role.MOTORISTA)).isTrue();
        assertThat(usuarioLogado.temPerfil(Role.ADMIN)).isFalse();
    }

    @Test
    void usuarioSemPerfilNaoTemNenhum() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("x", null, List.of()));

        assertThat(usuarioLogado.temPerfil(Role.MOTORISTA)).isFalse();
    }
}
