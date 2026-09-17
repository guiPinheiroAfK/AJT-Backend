package com.AJTBackend.config;

import com.AJTBackend.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void geraEValidaTokenComUsernameERole() {
        String token = jwtService.gerarToken("maria", Role.GERENTE);

        Claims claims = jwtService.validarToken(token);

        assertThat(claims.getSubject()).isEqualTo("maria");
        assertThat(claims.get("role", String.class)).isEqualTo("GERENTE");
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void rejeitaTokenExpirado() {
        JwtService expiraNaHora = new JwtService(SECRET, -1_000);
        String token = expiraNaHora.gerarToken("maria", Role.ADMIN);

        assertThatThrownBy(() -> jwtService.validarToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejeitaTokenAssinadoComOutraChave() {
        JwtService outraChave = new JwtService("outra-chave-totalmente-diferente-com-32-chars!!", 60_000);
        String token = outraChave.gerarToken("maria", Role.ADMIN);

        assertThatThrownBy(() -> jwtService.validarToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejeitaTokenMalformado() {
        assertThatThrownBy(() -> jwtService.validarToken("nao.e.um-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void falhaNaInicializacaoComChaveCurta() {
        assertThatThrownBy(() -> new JwtService("curta", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void tokenEmitidoAntesDaTrocaDeSenhaEhConsideradoAntigo() {
        Claims claims = jwtService.validarToken(jwtService.gerarToken("maria", Role.ADMIN));

        assertThat(jwtService.emitidoAntesDe(claims, LocalDateTime.now().plusMinutes(1))).isTrue();
        assertThat(jwtService.emitidoAntesDe(claims, LocalDateTime.now().minusMinutes(1))).isFalse();
        assertThat(jwtService.emitidoAntesDe(claims, null)).isFalse();
    }

    @Test
    void expiracaoEmSegundos() {
        assertThat(jwtService.getExpiracaoSegundos()).isEqualTo(60);
    }
}
