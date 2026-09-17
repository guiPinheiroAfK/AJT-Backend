package com.AJTBackend.exception;

public class CotacaoIndisponivelException extends RuntimeException {
    public CotacaoIndisponivelException(String moedaOrigem, String moedaDestino, Throwable causa) {
        super("Nao foi possivel obter a cotacao de " + moedaOrigem + " para " + moedaDestino
                + ": servico externo indisponivel", causa);
    }
}
