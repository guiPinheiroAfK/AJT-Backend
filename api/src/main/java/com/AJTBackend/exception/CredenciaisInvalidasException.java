package com.AJTBackend.exception;

public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException() {
        super("Username ou senha inválidos");
    }
}