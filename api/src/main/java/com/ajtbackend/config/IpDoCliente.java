package com.ajtbackend.config;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * IP de quem fez a requisicao, usado no bloqueio de tentativas de login.
 * Atras do nginx (EC2 e docker compose) todas as conexoes chegam vindas do proprio nginx,
 * entao o IP do cliente vem no cabecalho X-Real-IP que ele preenche. Esse cabecalho so vale
 * quando quem conectou e um proxy da rede interna; direto da internet, qualquer um poderia
 * forjar o cabecalho pra fugir do bloqueio.
 * (Nao usamos server.forward-headers-strategy: ele tambem troca porta e protocolo da
 * requisicao, e com o front em outra porta o Spring passava a ver o login como cross-origin.)
 */
public final class IpDoCliente {

    static final String CABECALHO = "X-Real-IP";

    // loopback e faixas privadas (RFC 1918), onde ficam o nginx da EC2 e a rede do docker
    private static final Pattern REDE_INTERNA = Pattern.compile(
            "^(127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.|0:0:0:0:0:0:0:1$|::1$)");

    private IpDoCliente() {
    }

    public static String de(HttpServletRequest request) {
        String conexao = request.getRemoteAddr();
        String informado = request.getHeader(CABECALHO);
        if (informado != null && !informado.isBlank() && REDE_INTERNA.matcher(conexao).find()) {
            return informado.trim();
        }
        return conexao;
    }
}
