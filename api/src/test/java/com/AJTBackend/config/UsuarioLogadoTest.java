package com.AJTBackend.config;

import com.AJTBackend.model.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioLogadoTest {

    private final UsuarioLogado usuarioLogado = new UsuarioLogado();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComAuthorities(String username, String... authorities) {
        List<SimpleGrantedAuthority> concedidas = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, concedidas));
    }

    private static void logadoComo(String username, String... roles) {
        String[] authorities = Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new);
        logadoComAuthorities(username, authorities);
    }

    // ----- ninguem logado -----

    @Test
    void semUsuarioLogadoOUsernameValeSistema() {
        // When
        String username = usuarioLogado.usernameOuSistema();

        // Then
        assertThat(username).isEqualTo("sistema");
    }

    @Test
    void semUsuarioLogadoNinguemEhOUsuarioInformado() {
        // When
        boolean ehUsuario = usuarioLogado.ehUsuario("admin");

        // Then
        assertThat(ehUsuario).isFalse();
    }

    @Test
    void semUsuarioLogadoNaoHaPerfilNenhum() {
        // When
        boolean temPerfil = usuarioLogado.temPerfil(Role.ADMIN);

        // Then
        assertThat(temPerfil).isFalse();
    }

    // ----- username -----

    @Test
    void devolveOUsernameDoUsuarioLogado() {
        // Given
        logadoComo("maria", "GERENTE");

        // When
        String username = usuarioLogado.usernameOuSistema();

        // Then
        assertThat(username).isEqualTo("maria");
    }

    @Test
    void ehUsuarioConfirmaOProprioUsername() {
        // Given
        logadoComo("maria", "GERENTE");

        // When
        boolean ehUsuario = usuarioLogado.ehUsuario("maria");

        // Then
        assertThat(ehUsuario).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"joao", "Maria", " maria", "maria ", ""})
    void ehUsuarioRecusaQualquerOutroUsernameComparandoExatamente(String outro) {
        // Given
        logadoComo("maria", "GERENTE");

        // When
        boolean ehUsuario = usuarioLogado.ehUsuario(outro);

        // Then
        assertThat(ehUsuario).isFalse();
    }

    @Test
    void ehUsuarioComUsernameNuloEhFalso() {
        // Given
        logadoComo("maria", "GERENTE");

        // When
        boolean ehUsuario = usuarioLogado.ehUsuario(null);

        // Then
        assertThat(ehUsuario).isFalse();
    }

    // ----- perfil -----

    @Test
    void temPerfilConfereAAuthorityComPrefixoRole() {
        // Given
        logadoComo("carlos", "MOTORISTA");

        // When
        boolean temPerfil = usuarioLogado.temPerfil(Role.MOTORISTA);

        // Then
        assertThat(temPerfil).isTrue();
    }

    @Test
    void naoTemOPerfilQueNaoFoiConcedido() {
        // Given
        logadoComo("carlos", "MOTORISTA");

        // When
        boolean temPerfil = usuarioLogado.temPerfil(Role.ADMIN);

        // Then
        assertThat(temPerfil).isFalse();
    }

    @Test
    void usuarioComVariosPerfisTemCadaUmDeles() {
        // Given
        logadoComo("ana", "GERENTE", "ATENDENTE");

        // When
        boolean gerente = usuarioLogado.temPerfil(Role.GERENTE);
        boolean atendente = usuarioLogado.temPerfil(Role.ATENDENTE);
        boolean admin = usuarioLogado.temPerfil(Role.ADMIN);

        // Then
        assertThat(gerente).isTrue();
        assertThat(atendente).isTrue();
        assertThat(admin).isFalse();
    }

    @Test
    void authoritySemOPrefixoRoleNaoContaComoPerfil() {
        // Given: "MOTORISTA" sozinho nao e o mesmo que "ROLE_MOTORISTA"
        logadoComAuthorities("carlos", "MOTORISTA");

        // When
        boolean temPerfil = usuarioLogado.temPerfil(Role.MOTORISTA);

        // Then
        assertThat(temPerfil).isFalse();
    }

    @Test
    void usuarioSemPerfilNaoTemNenhum() {
        // Given
        logadoComAuthorities("x");

        // When
        boolean temPerfil = usuarioLogado.temPerfil(Role.MOTORISTA);

        // Then
        assertThat(temPerfil).isFalse();
    }
}