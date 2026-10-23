package com.AJTBackend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: o contador de tentativas de login (LoginAttemptService) que
 * bloqueia forca bruta — bloqueio ao atingir o limite, reset apos sucesso,
 * isolamento por ip+usuario e case-insensitive no username.
 *
 * como rodar: teste unitario puro (cache em memoria via caffeine, sem
 * spring, sem banco, sem docker). roda com "mvn test".
 *
 * por que existe: e a unica barreira contra tentativa de adivinhar senha
 * (ver AuthService). se o bloqueio nao isolar por ip+usuario direito, ou
 * nao resetar no sucesso, ou se comparar username com case sensitivo, o
 * proprio dono da conta pode acabar bloqueado, ou o bloqueio nao funciona.
 */
class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.1";

    private final LoginAttemptService service = new LoginAttemptService(3, 15);

    private void falhar(String ip, String username, int vezes) {
        for (int i = 0; i < vezes; i++) {
            service.registrarFalha(ip, username);
        }
    }

    @Test
    void duasFalhasAindaNaoBloqueiamOLogin() {
        // Given
        falhar(IP, "admin", 2);

        // When
        boolean bloqueado = service.bloqueado(IP, "admin");

        // Then
        assertThat(bloqueado).isFalse();
    }

    @Test
    void terceiraFalhaSeguidaBloqueiaOLogin() {
        // Given
        falhar(IP, "admin", 3);

        // When
        boolean bloqueado = service.bloqueado(IP, "admin");

        // Then
        assertThat(bloqueado).isTrue();
    }

    @Test
    void loginComSucessoZeraAsFalhasAnteriores() {
        // Given
        falhar(IP, "admin", 2);
        service.registrarSucesso(IP, "admin");
        falhar(IP, "admin", 1);

        // When
        boolean bloqueado = service.bloqueado(IP, "admin");

        // Then
        assertThat(bloqueado).isFalse();
    }

    @Test
    void bloqueioNaoAtingeOutroIpNemOutroUsuario() {
        // Given
        falhar(IP, "admin", 3);

        // When
        boolean outroIp = service.bloqueado("10.0.0.2", "admin");
        boolean outroUsuario = service.bloqueado(IP, "maria");

        // Then
        assertThat(outroIp).isFalse();
        assertThat(outroUsuario).isFalse();
    }

    @Test
    void usernameAusenteTemContadorProprio() {
        // Given
        falhar(IP, null, 3);

        // When
        boolean semUsername = service.bloqueado(IP, null);
        boolean admin = service.bloqueado(IP, "admin");

        // Then
        assertThat(semUsername).isTrue();
        assertThat(admin).isFalse();
    }

    @Test
    void usernameNaoDiferenciaMaiusculas() {
        // Given
        falhar(IP, "Admin", 3);

        // When
        boolean bloqueado = service.bloqueado(IP, "ADMIN");

        // Then
        assertThat(bloqueado).isTrue();
    }
}
