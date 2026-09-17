package com.AJTBackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita tentativas de login por IP + username (protecao contra forca bruta).
 * Em memoria: suficiente pra uma instancia; com varias instancias, mover pra Redis.
 */
@Service
public class LoginAttemptService {

    private final int maxTentativas;
    private final Cache<String, AtomicInteger> falhas;

    public LoginAttemptService(@Value("${ajt.login.max-tentativas}") int maxTentativas,
                               @Value("${ajt.login.bloqueio-minutos}") long bloqueioMinutos) {
        this.maxTentativas = maxTentativas;
        this.falhas = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(bloqueioMinutos))
                .maximumSize(10_000)
                .build();
    }

    public boolean bloqueado(String ip, String username) {
        AtomicInteger contador = falhas.getIfPresent(chave(ip, username));
        return contador != null && contador.get() >= maxTentativas;
    }

    public void registrarFalha(String ip, String username) {
        String chave = chave(ip, username);
        AtomicInteger contador = falhas.get(chave, k -> new AtomicInteger());
        contador.incrementAndGet();
        falhas.put(chave, contador); // renova a janela de bloqueio a cada falha
    }

    public void registrarSucesso(String ip, String username) {
        falhas.invalidate(chave(ip, username));
    }

    private String chave(String ip, String username) {
        return ip + "|" + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }
}
