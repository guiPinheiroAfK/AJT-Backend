package com.AJTBackend.exception;

public class UsuarioNaoEncontradoException extends RecursoNaoEncontradoException {
    public UsuarioNaoEncontradoException(Long id) {
        super("Usuário não encontrado com id: " + id);
    }
}