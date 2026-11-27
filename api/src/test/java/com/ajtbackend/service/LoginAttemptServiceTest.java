package com.ajtbackend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    // o bloqueio vale pro par IP + usuario: 3 falhas bloqueiam so aquele par
    @ParameterizedTest(name = "{0} falhas de {1}/admin, consulta {2}/{3} -> bloqueado={4}")
    @CsvSource({
            "2, 10.0.0.1, 10.0.0.1, admin, false", // abaixo do limite
            "3, 10.0.0.1, 10.0.0.1, admin, true",  // atingiu o limite
            "3, 10.0.0.1, 10.0.0.2, admin, false", // mesmo usuario em outro IP
            "3, 10.0.0.1, 10.0.0.1, maria, false"  // outro usuario no mesmo IP
    })
    void deveBloquearSoOParIpUsuario_quandoFalhasAtingemOLimite(int falhas, String ipDasFalhas, String ip, String usuario,
                                                                boolean esperado) {
        // given
        falhar(ipDasFalhas, "admin", falhas);

        // when
        boolean bloqueado = service.bloqueado(ip, usuario);

        // then
        assertThat(bloqueado).isEqualTo(esperado);
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

    // ---------------- limite por IP (varias contas) ----------------

    @Test
    void deveBloquearOIpEmQualquerConta_quandoErraEmMuitasContasDiferentes() {
        // given: 3 x 10 = 30 erros espalhados em 30 usernames, nenhum deles chegou ao limite da conta
        for (int i = 0; i < 30; i++) {
            service.registrarFalha(IP, "usuario" + i);
        }

        // when
        boolean mesmoIp = service.bloqueado(IP, "admin");
        boolean outroIp = service.bloqueado("10.0.0.9", "admin");

        // then: o IP para de gastar BCrypt e nao consegue mais expulsar chaves do cache
        assertThat(mesmoIp).isTrue();
        assertThat(outroIp).isFalse();
    }

    @Test
    void naoDeveLiberarOIp_quandoAcertaASenhaDeUmaConta() {
        // given
        for (int i = 0; i < 30; i++) {
            service.registrarFalha(IP, "usuario" + i);
        }

        // when
        service.registrarSucesso(IP, "minha-conta");

        // then: logar na propria conta nao zera o limite do IP
        assertThat(service.bloqueado(IP, "admin")).isTrue();
    }
}
