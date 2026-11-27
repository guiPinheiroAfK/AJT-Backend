package com.ajtbackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita tentativas de login (protecao contra forca bruta) em dois niveis:
 * - IP + username: bloqueia depois de max-tentativas erros na mesma conta;
 * - so o IP: bloqueia depois de FATOR_POR_IP vezes isso, somando todas as contas. Sem esse
 *   limite, um IP podia trocar de username a cada tentativa (cada erro custa um BCrypt) e
 *   ainda encher o cache ate expulsar a chave de uma conta ja bloqueada.
 * O IP vem do X-Real-IP quando a API esta atras do nginx (ver IpDoCliente).
 * Em memoria: suficiente pra uma instancia; com varias instancias, mover pra Redis.
 */
@Service
public class LoginAttemptService {

    private static final int FATOR_POR_IP = 10;

    private final int maxTentativas;
    private final Cache<String, AtomicInteger> falhas;
    private final Cache<String, AtomicInteger> falhasPorIp;

    public LoginAttemptService(@Value("${ajt.login.max-tentativas}") int maxTentativas,
                               @Value("${ajt.login.bloqueio-minutos}") long bloqueioMinutos) {
        this.maxTentativas = maxTentativas;
        this.falhas = novoCache(bloqueioMinutos);
        this.falhasPorIp = novoCache(bloqueioMinutos);
    }

    private static Cache<String, AtomicInteger> novoCache(long bloqueioMinutos) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(bloqueioMinutos))
                .maximumSize(10_000)
                .build();
    }

    public boolean bloqueado(String ip, String username) {
        return atingiu(falhas, chave(ip, username), maxTentativas)
                || atingiu(falhasPorIp, ip, maxTentativas * FATOR_POR_IP);
    }

    public void registrarFalha(String ip, String username) {
        incrementar(falhas, chave(ip, username));
        incrementar(falhasPorIp, ip);
    }

    // acertar a senha libera a conta, mas nao zera o limite do IP (senao bastaria logar
    // numa conta propria pra continuar tentando nas outras)
    public void registrarSucesso(String ip, String username) {
        falhas.invalidate(chave(ip, username));
    }

    private static boolean atingiu(Cache<String, AtomicInteger> cache, String chave, int limite) {
        AtomicInteger contador = cache.getIfPresent(chave);
        return contador != null && contador.get() >= limite;
    }

    private static void incrementar(Cache<String, AtomicInteger> cache, String chave) {
        AtomicInteger contador = cache.get(chave, k -> new AtomicInteger());
        contador.incrementAndGet();
        cache.put(chave, contador); // renova a janela de bloqueio a cada falha
    }

    private String chave(String ip, String username) {
        return ip + "|" + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }
}
