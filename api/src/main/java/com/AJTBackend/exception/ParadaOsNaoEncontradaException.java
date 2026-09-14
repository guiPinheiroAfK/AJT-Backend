package com.AJTBackend.exception;

public class ParadaOsNaoEncontradaException extends RecursoNaoEncontradoException {
    public ParadaOsNaoEncontradaException(Long id) {
        super("Parada de OS não encontrada com id: " + id);
    }
}
