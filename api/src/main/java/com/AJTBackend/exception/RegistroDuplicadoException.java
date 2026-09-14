package com.AJTBackend.exception;

public abstract class RegistroDuplicadoException extends RuntimeException {
    protected RegistroDuplicadoException(String mensagem) {
        super(mensagem);
    }
}
