package com.AJTBackend.exception;

public class PassageiroNaoEncontradoException extends RuntimeException {

    public PassageiroNaoEncontradoException(Long id) {
        super("Passageiro não encontrado com id: " + id);
    }
}