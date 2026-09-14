package com.AJTBackend.exception;

public class PontoColetaNaoEncontradoException extends RecursoNaoEncontradoException {
    public PontoColetaNaoEncontradoException(Long id) {
        super("Ponto de coleta não encontrado com id: " + id);
    }
}
