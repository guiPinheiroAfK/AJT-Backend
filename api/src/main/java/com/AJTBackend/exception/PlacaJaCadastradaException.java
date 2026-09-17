package com.AJTBackend.exception;

public class PlacaJaCadastradaException extends RegistroDuplicadoException {
    public PlacaJaCadastradaException(String placa) {
        super("Já existe veículo cadastrado com placa: " + placa);
    }
}