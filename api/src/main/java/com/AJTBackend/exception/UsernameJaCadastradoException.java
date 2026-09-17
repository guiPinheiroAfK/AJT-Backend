package com.AJTBackend.exception;

public class UsernameJaCadastradoException extends RegistroDuplicadoException {
    public UsernameJaCadastradoException(String username) {
        super("Já existe usuário cadastrado com username: " + username);
    }
}