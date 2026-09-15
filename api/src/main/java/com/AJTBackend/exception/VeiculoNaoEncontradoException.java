package com.AJTBackend.exception;

public class VeiculoNaoEncontradoException extends RecursoNaoEncontradoException {
    public VeiculoNaoEncontradoException(Long id) {
        super("Veículo não encontrado com id: " + id);
    }

    public VeiculoNaoEncontradoException(String placa) {
        super("Veículo não encontrado com placa: " + placa);
    }
}