package com.AJTBackend.exception;

public class OrdemServicoNaoEncontradoException extends RuntimeException {
    public OrdemServicoNaoEncontradoException(Long id) {
        super("Ordem de serviço não encontrada com id: " + id);
    }
}
