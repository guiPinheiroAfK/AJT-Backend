package com.AJTBackend.exception;

// a mensagem nao repete o numero: documento e dado sensivel (LGPD)
public class DocumentoJaCadastradoException extends RegistroDuplicadoException {
    public DocumentoJaCadastradoException(String tipoDocumento) {
        super("Já existe passageiro cadastrado com este " + tipoDocumento + ".");
    }
}
