package com.AJTBackend.exception;

public class OrdemServicoNaoEncontradoException extends RecursoNaoEncontradoException {
    public OrdemServicoNaoEncontradoException(Long id) {
        super("Ordem de serviço não encontrada com id: " + id);
    }
}
