package com.AJTBackend.exception;

public class ParadaOsNaoEncontradaException extends RuntimeException {
    public ParadaOsNaoEncontradaException(Long id) {
        super("Parada de OS não encontrada com id: " + id);
    }
}
