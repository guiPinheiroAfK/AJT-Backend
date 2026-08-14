package AJTBackend.exception;

public class PlacaJaCadastradaException extends RuntimeException {
    public PlacaJaCadastradaException(String placa) {
        super("Já existe veículo cadastrado com placa: " + placa);
    }
}