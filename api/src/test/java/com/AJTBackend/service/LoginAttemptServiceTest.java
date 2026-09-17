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

    private final LoginAttemptService service = new LoginAttemptService(3, 15);

    @Test
    void bloqueiaAposAtingirOLimiteDeFalhas() {
        service.registrarFalha("10.0.0.1", "admin");
        service.registrarFalha("10.0.0.1", "admin");
        assertThat(service.bloqueado("10.0.0.1", "admin")).isFalse();

        service.registrarFalha("10.0.0.1", "admin");
        assertThat(service.bloqueado("10.0.0.1", "admin")).isTrue();
    }

    @Test
    void sucessoZeraAsFalhas() {
        service.registrarFalha("10.0.0.1", "admin");
        service.registrarFalha("10.0.0.1", "admin");
        service.registrarSucesso("10.0.0.1", "admin");
        service.registrarFalha("10.0.0.1", "admin");

        assertThat(service.bloqueado("10.0.0.1", "admin")).isFalse();
    }

    @Test
    void bloqueioEhPorIpEUsername() {
        for (int i = 0; i < 3; i++) {
            service.registrarFalha("10.0.0.1", "admin");
        }

        assertThat(service.bloqueado("10.0.0.2", "admin")).isFalse(); // outro IP
        assertThat(service.bloqueado("10.0.0.1", "maria")).isFalse(); // outro usuario
    }

    @Test
    void usernameNaoDiferenciaMaiusculas() {
        for (int i = 0; i < 3; i++) {
            service.registrarFalha("10.0.0.1", "Admin");
        }

        assertThat(service.bloqueado("10.0.0.1", "ADMIN")).isTrue();
    }
}
