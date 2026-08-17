package com.AJTBackend.exception;

public class VeiculoNaoEncontradoException extends RuntimeException {
    public VeiculoNaoEncontradoException(Long id) {
        super("Veículo não encontrado com id: " + id);
    }
}