package com.AJTBackend.exception;

public class MuitasTentativasException extends RuntimeException {
    public MuitasTentativasException() {
        super("Muitas tentativas de login. Aguarde alguns minutos e tente novamente");
    }
}
