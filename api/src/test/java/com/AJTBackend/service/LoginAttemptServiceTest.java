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

    @Test
    void naoDeveBloquear_quandoFalhasFicamAbaixoDoLimite() {
        // given
        falhar(IP, "admin", 2);

        // when
        boolean bloqueado = service.bloqueado(IP, "admin");

        // then
        assertThat(bloqueado).isFalse();
    }

    @Test
    void deveBloquear_quandoFalhasAtingemOLimite() {
        // given
        falhar(IP, "admin", 3);

        // when
        boolean bloqueado = service.bloqueado(IP, "admin");

        // then
        assertThat(bloqueado).isTrue();
    }

    @Test
    void naoDeveBloquear_quandoSucessoZerouAsFalhasAnteriores() {
        // given
        falhar(IP, "admin", 2);
        service.registrarSucesso(IP, "admin");
        falhar(IP, "admin", 1);

        // when
        boolean bloqueado = service.bloqueado(IP, "admin");

        // then
        assertThat(bloqueado).isFalse();
    }

    @Test
    void naoDeveBloquearOutroIp_quandoMesmoUsuarioFoiBloqueado() {
        // given
        falhar(IP, "admin", 3);

        // when
        boolean bloqueado = service.bloqueado("10.0.0.2", "admin");

        // then
        assertThat(bloqueado).isFalse();
    }

    @Test
    void naoDeveBloquearOutroUsuario_quandoMesmoIpFoiBloqueado() {
        // given
        falhar(IP, "admin", 3);

        // when
        boolean bloqueado = service.bloqueado(IP, "maria");

        // then
        assertThat(bloqueado).isFalse();
    }

    @Test
    void deveBloquear_quandoFalhasSemUsernameAtingemOLimite() {
        // given
        falhar(IP, null, 3);

        // when
        boolean bloqueado = service.bloqueado(IP, null);

        // then
        assertThat(bloqueado).isTrue();
    }

    @Test
    void naoDeveBloquearUsuarioNomeado_quandoSoOContadorSemUsernameEstourou() {
        // given
        falhar(IP, null, 3);

        // when
        boolean bloqueado = service.bloqueado(IP, "admin");

        // then
        assertThat(bloqueado).isFalse();
    }

    @Test
    void deveBloquear_quandoUsernameChegaComOutraCaixa() {
        // given
        falhar(IP, "Admin", 3);

        // when
        boolean bloqueado = service.bloqueado(IP, "ADMIN");

        // then
        assertThat(bloqueado).isTrue();
    }

    private void falhar(String ip, String username, int vezes) {
        for (int i = 0; i < vezes; i++) {
            service.registrarFalha(ip, username);
        }
    }
}
