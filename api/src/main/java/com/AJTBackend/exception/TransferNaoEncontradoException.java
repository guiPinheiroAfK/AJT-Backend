package com.AJTBackend.exception;

public class TransferNaoEncontradoException extends RuntimeException {
    public TransferNaoEncontradoException(Long id) {
        super("Transfer não encontrado com id: " + id);
    }
}
