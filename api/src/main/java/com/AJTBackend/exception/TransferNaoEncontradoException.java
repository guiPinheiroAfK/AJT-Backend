package com.AJTBackend.exception;

public class TransferNaoEncontradoException extends RecursoNaoEncontradoException {
    public TransferNaoEncontradoException(Long id) {
        super("Transfer não encontrado com id: " + id);
    }
}
