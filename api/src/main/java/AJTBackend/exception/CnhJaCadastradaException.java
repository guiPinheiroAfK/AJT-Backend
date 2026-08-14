package AJTBackend.exception;

public class CnhJaCadastradaException extends RuntimeException {
    public CnhJaCadastradaException(String cnh) {
        super("Já existe motorista cadastrado com CNH: " + cnh);
    }
}