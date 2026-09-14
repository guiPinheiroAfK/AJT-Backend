package com.AJTBackend.exception;

public class MotoristaNaoEncontradoException extends RecursoNaoEncontradoException {
    public MotoristaNaoEncontradoException(Long id) {
        super("Motorista não encontrado com id: " + id);
    }
}