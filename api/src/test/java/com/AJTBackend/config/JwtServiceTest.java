package com.AJTBackend.config;

import com.AJTBackend.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * o que testa: geracao e validacao de token jwt (JwtService), incluindo
 * assinatura, expiracao, chave errada, token malformado e a regra que
 * invalida tokens emitidos antes de uma troca de senha.
 *
 * como rodar: teste unitario puro (sem spring, sem banco, sem docker).
 * roda com "mvn test" normalmente, em qualquer maquina.
 *
 * por que existe: JwtService e a base de toda a autenticacao da api — um
 * bug aqui derruba o login inteiro ou, pior, aceita token invalido/expirado.
 * como e codigo critico e facil de testar isolado, vale a pena cobrir bem
 * aqui em vez de depender so dos testes de integracao pra pegar esses casos.
 */
class JwtServiceTest {

    private static final String SECRET = "chave-de-teste-com-mais-de-32-caracteres-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void deveDevolverUsernameERole_quandoValidaTokenRecemGerado() {
        // given
        String token = jwtService.gerarToken("maria", Role.GERENTE);

        // when
        Claims claims = jwtService.validarToken(token);

        // then
        assertThat(claims.getSubject()).isEqualTo("maria");
        assertThat(claims.get("role", String.class)).isEqualTo("GERENTE");
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void deveLancarExpiredJwt_quandoTokenJaExpirou() {
        // given
        JwtService expiraNaHora = new JwtService(SECRET, -1_000);
        String token = expiraNaHora.gerarToken("maria", Role.ADMIN);

        // when
        Throwable erro = catchThrowable(() -> jwtService.validarToken(token));

        // then
        assertThat(erro).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void deveLancarJwtException_quandoTokenFoiAssinadoComOutraChave() {
        // given
        JwtService outraChave = new JwtService("outra-chave-totalmente-diferente-com-32-chars!!", 60_000);
        String token = outraChave.gerarToken("maria", Role.ADMIN);

        // when
        Throwable erro = catchThrowable(() -> jwtService.validarToken(token));

        // then
        assertThat(erro).isInstanceOf(JwtException.class);
    }

    @Test
    void deveLancarJwtException_quandoTokenEstaMalformado() {
        // when
        Throwable erro = catchThrowable(() -> jwtService.validarToken("nao.e.um-jwt"));

        // then
        assertThat(erro).isInstanceOf(JwtException.class);
    }

    @Test
    void naoDeveInicializar_quandoChaveTemMenosDe32Caracteres() {
        // when
        Throwable erro = catchThrowable(() -> new JwtService("curta", 60_000));

        // then
        assertThat(erro)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void naoDeveInicializar_quandoChaveNaoFoiConfigurada() {
        // when
        Throwable erro = catchThrowable(() -> new JwtService(null, 60_000));

        // then
        assertThat(erro)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AJT_JWT_SECRET");
    }

    @Test
    void deveConsiderarTokenAntigo_quandoSenhaFoiTrocadaDepoisDaEmissao() {
        // given
        Claims claims = jwtService.validarToken(jwtService.gerarToken("maria", Role.ADMIN));

        // when
        boolean antigo = jwtService.emitidoAntesDe(claims, LocalDateTime.now().plusMinutes(1));

        // then
        assertThat(antigo).isTrue();
    }

    @Test
    void naoDeveConsiderarTokenAntigo_quandoSenhaFoiTrocadaAntesDaEmissao() {
        // given
        Claims claims = jwtService.validarToken(jwtService.gerarToken("maria", Role.ADMIN));

        // when
        boolean antigo = jwtService.emitidoAntesDe(claims, LocalDateTime.now().minusMinutes(1));

        // then
        assertThat(antigo).isFalse();
    }

    @Test
    void naoDeveConsiderarTokenAntigo_quandoSenhaNuncaFoiTrocada() {
        // given
        Claims claims = jwtService.validarToken(jwtService.gerarToken("maria", Role.ADMIN));

        // when
        boolean antigo = jwtService.emitidoAntesDe(claims, null);

        // then
        assertThat(antigo).isFalse();
    }

    @Test
    void naoDeveConsiderarTokenAntigo_quandoTokenNaoTemDataDeEmissao() {
        // given
        Claims semIat = mock(Claims.class);
        when(semIat.getIssuedAt()).thenReturn(null);

        // when
        boolean antigo = jwtService.emitidoAntesDe(semIat, LocalDateTime.now());

        // then
        assertThat(antigo).isFalse();
    }

    @Test
    void deveInformarExpiracaoEmSegundos_quandoConfiguradaEmMilissegundos() {
        // when
        long segundos = jwtService.getExpiracaoSegundos();

        // then
        assertThat(segundos).isEqualTo(60);
    }
}
