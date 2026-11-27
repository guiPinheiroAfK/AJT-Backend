package com.ajtbackend.exception;

public abstract class RegistroDuplicadoException extends RuntimeException {
    protected RegistroDuplicadoException(String mensagem) {
        super(mensagem);
    }
}
