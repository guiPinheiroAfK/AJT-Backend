package com.AJTBackend.exception;

public class CnhJaCadastradaException extends RegistroDuplicadoException {
    public CnhJaCadastradaException(String cnh) {
        super("Já existe motorista cadastrado com CNH: " + cnh);
    }
}