package com.AJTBackend.config;

import com.AJTBackend.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void geraEValidaTokenComUsernameERole() {
        // Given
        String token = jwtService.gerarToken("maria", Role.GERENTE);

        // When
        Claims claims = jwtService.validarToken(token);

        // Then
        assertThat(claims.getSubject()).isEqualTo("maria");
        assertThat(claims.get("role", String.class)).isEqualTo("GERENTE");
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void rejeitaTokenExpirado() {
        // Given
        JwtService expiraNaHora = new JwtService(SECRET, -1_000);
        String token = expiraNaHora.gerarToken("maria", Role.ADMIN);

        // When / Then
        assertThatThrownBy(() -> jwtService.validarToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejeitaTokenAssinadoComOutraChave() {
        // Given
        JwtService outraChave = new JwtService("outra-chave-totalmente-diferente-com-32-chars!!", 60_000);
        String token = outraChave.gerarToken("maria", Role.ADMIN);

        // When / Then
        assertThatThrownBy(() -> jwtService.validarToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejeitaTokenMalformado() {
        // Given
        String malformado = "nao.e.um-jwt";

        // When / Then
        assertThatThrownBy(() -> jwtService.validarToken(malformado)).isInstanceOf(JwtException.class);
    }

    @Test
    void falhaNaInicializacaoComChaveCurta() {
        // Given
        String chaveCurta = "curta";

        // When / Then
        assertThatThrownBy(() -> new JwtService(chaveCurta, 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void falhaNaInicializacaoSemChave() {
        // When / Then
        assertThatThrownBy(() -> new JwtService(null, 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AJT_JWT_SECRET");
    }

    @Test
    void tokenEmitidoAntesDaTrocaDeSenhaEhConsideradoAntigo() {
        // Given
        Claims claims = jwtService.validarToken(jwtService.gerarToken("maria", Role.ADMIN));
        LocalDateTime senhaTrocadaDepois = LocalDateTime.now().plusMinutes(1);

        // When
        boolean antigo = jwtService.emitidoAntesDe(claims, senhaTrocadaDepois);

        // Then
        assertThat(antigo).isTrue();
    }

    @Test
    void tokenEmitidoDepoisDaTrocaDeSenhaContinuaValendo() {
        // Given
        Claims claims = jwtService.validarToken(jwtService.gerarToken("maria", Role.ADMIN));
        LocalDateTime senhaTrocadaAntes = LocalDateTime.now().minusMinutes(1);

        // When
        boolean antigo = jwtService.emitidoAntesDe(claims, senhaTrocadaAntes);

        // Then
        assertThat(antigo).isFalse();
    }

    @Test
    void usuarioQueNuncaTrocouASenhaNaoTemTokenAntigo() {
        // Given
        Claims claims = jwtService.validarToken(jwtService.gerarToken("maria", Role.ADMIN));

        // When
        boolean antigo = jwtService.emitidoAntesDe(claims, null);

        // Then
        assertThat(antigo).isFalse();
    }

    @Test
    void tokenSemDataDeEmissaoNaoEhConsideradoAntigo() {
        // Given
        Claims semIat = mock(Claims.class);
        when(semIat.getIssuedAt()).thenReturn(null);

        // When
        boolean antigo = jwtService.emitidoAntesDe(semIat, LocalDateTime.now());

        // Then
        assertThat(antigo).isFalse();
    }

    @Test
    void expiracaoEhInformadaEmSegundos() {
        // When
        long segundos = jwtService.getExpiracaoSegundos();

        // Then
        assertThat(segundos).isEqualTo(60);
    }
}
