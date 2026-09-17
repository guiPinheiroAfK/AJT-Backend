package com.AJTBackend.exception;

public class PontoColetaNaoEncontradoException extends RuntimeException {
    public PontoColetaNaoEncontradoException(Long id) {
        super("Ponto de coleta não encontrado com id: " + id);
    }
}
