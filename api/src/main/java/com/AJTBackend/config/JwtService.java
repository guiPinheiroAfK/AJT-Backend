package com.AJTBackend.config;

import com.AJTBackend.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;

@Service
public class JwtService {

    private static final int TAMANHO_MINIMO_CHAVE = 32; // HS256 exige >= 256 bits

    private final SecretKey chave;
    private final long expiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expiration) {
        // falha na subida da aplicacao (e nao a cada request) se a chave for fraca
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_CHAVE) {
            throw new IllegalStateException("jwt.secret (AJT_JWT_SECRET) deve ter pelo menos "
                    + TAMANHO_MINIMO_CHAVE + " caracteres");
        }
        this.chave = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String gerarToken(String username, Role role) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chave)
                .compact();
    }

    /**
     * valida assinatura e expiracao numa unica leitura do token.
     *
     * @throws JwtException se o token for invalido, expirado ou malformado
     */
    public Claims validarToken(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * true se o token foi emitido antes da ultima troca de senha do usuario
     * (tokens antigos deixam de valer). O iat do JWT tem precisao de segundos.
     */
    public boolean emitidoAntesDe(Claims claims, LocalDateTime instante) {
        if (instante == null || claims.getIssuedAt() == null) {
            return false;
        }
        Date limite = Date.from(instante.truncatedTo(ChronoUnit.SECONDS)
                .atZone(ZoneId.systemDefault()).toInstant());
        return claims.getIssuedAt().before(limite);
    }

    public long getExpiracaoSegundos() {
        return expiration / 1000;
    }
}
