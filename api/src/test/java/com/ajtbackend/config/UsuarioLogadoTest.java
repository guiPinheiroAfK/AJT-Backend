package com.ajtbackend.config;

import com.ajtbackend.model.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: o UsuarioLogado, unico ponto que le o SecurityContextHolder:
 * username de quem esta logado (ou "sistema"), comparacao exata do username
 * e checagem de perfil pela authority com prefixo ROLE_.
 *
 * como rodar: teste unitario, monta o SecurityContext na mao. sem spring
 * context, sem banco. "mvn test".
 *
 * por que existe: as regras "ninguem se exclui" e "motorista so muda status"
 * dependem dessas tres respostas. uma comparacao frouxa (maiusculas, espaco)
 * ou um perfil sem prefixo aceito abriria brecha de permissao.
 */
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
    void deveDevolverSistema_quandoNinguemEstaLogado() {
        // when
        String username = usuarioLogado.usernameOuSistema();

        // then
        assertThat(username).isEqualTo("sistema");
    }

    @Test
    void naoDeveReconhecerUsuario_quandoNinguemEstaLogado() {
        // when
        boolean ehUsuario = usuarioLogado.ehUsuario("admin");

        // then
        assertThat(ehUsuario).isFalse();
    }

    @Test
    void naoDeveTerPerfil_quandoNinguemEstaLogado() {
        // when
        boolean temPerfil = usuarioLogado.temPerfil(Role.ADMIN);

        // then
        assertThat(temPerfil).isFalse();
    }

    // ----- username -----

    @Test
    void deveDevolverOUsername_quandoHaUsuarioLogado() {
        // given
        logadoComo("maria", "GERENTE");

        // when
        String username = usuarioLogado.usernameOuSistema();

        // then
        assertThat(username).isEqualTo("maria");
    }

    @Test
    void deveReconhecerUsuario_quandoUsernameEhODoLogado() {
        // given
        logadoComo("maria", "GERENTE");

        // when
        boolean ehUsuario = usuarioLogado.ehUsuario("maria");

        // then
        assertThat(ehUsuario).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" nao e a maria")
    @ValueSource(strings = {"joao", "Maria", " maria", "maria ", ""})
    void naoDeveReconhecerUsuario_quandoUsernameNaoEhExatamenteODoLogado(String outro) {
        // given
        logadoComo("maria", "GERENTE");

        // when
        boolean ehUsuario = usuarioLogado.ehUsuario(outro);

        // then
        assertThat(ehUsuario).isFalse();
    }

    @Test
    void naoDeveReconhecerUsuario_quandoUsernameInformadoEhNulo() {
        // given
        logadoComo("maria", "GERENTE");

        // when
        boolean ehUsuario = usuarioLogado.ehUsuario(null);

        // then
        assertThat(ehUsuario).isFalse();
    }

    // ----- perfil -----

    @Test
    void deveTerOPerfil_quandoAuthorityTemPrefixoRole() {
        // given
        logadoComo("carlos", "MOTORISTA");

        // when
        boolean temPerfil = usuarioLogado.temPerfil(Role.MOTORISTA);

        // then
        assertThat(temPerfil).isTrue();
    }

    @Test
    void naoDeveTerOPerfil_quandoEleNaoFoiConcedido() {
        // given
        logadoComo("carlos", "MOTORISTA");

        // when
        boolean temPerfil = usuarioLogado.temPerfil(Role.ADMIN);

        // then
        assertThat(temPerfil).isFalse();
    }

    @ParameterizedTest(name = "quem e GERENTE e ATENDENTE tem {0}")
    @EnumSource(value = Role.class, names = {"GERENTE", "ATENDENTE"})
    void deveTerCadaPerfil_quandoUsuarioTemVarios(Role perfil) {
        // given
        logadoComo("ana", "GERENTE", "ATENDENTE");

        // when
        boolean temPerfil = usuarioLogado.temPerfil(perfil);

        // then
        assertThat(temPerfil).isTrue();
    }

    @Test
    void naoDeveTerPerfilFora_quandoUsuarioTemVarios() {
        // given
        logadoComo("ana", "GERENTE", "ATENDENTE");

        // when
        boolean admin = usuarioLogado.temPerfil(Role.ADMIN);

        // then
        assertThat(admin).isFalse();
    }

    @Test
    void naoDeveContarComoPerfil_quandoAuthorityNaoTemPrefixoRole() {
        // given
        // "MOTORISTA" sozinho nao e o mesmo que "ROLE_MOTORISTA"
        logadoComAuthorities("carlos", "MOTORISTA");

        // when
        boolean temPerfil = usuarioLogado.temPerfil(Role.MOTORISTA);

        // then
        assertThat(temPerfil).isFalse();
    }

    @Test
    void naoDeveTerPerfil_quandoUsuarioNaoTemNenhumaAuthority() {
        // given
        logadoComAuthorities("x");

        // when
        boolean temPerfil = usuarioLogado.temPerfil(Role.MOTORISTA);

        // then
        assertThat(temPerfil).isFalse();
    }
}
