package com.AJTBackend.exception;

public class PassageiroNaoEncontradoException extends RecursoNaoEncontradoException {

    public PassageiroNaoEncontradoException(Long id) {
        super("Passageiro não encontrado com id: " + id);
    }
}