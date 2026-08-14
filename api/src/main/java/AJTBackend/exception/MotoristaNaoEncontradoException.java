package AJTBackend.exception;

public class MotoristaNaoEncontradoException extends RuntimeException {
    public MotoristaNaoEncontradoException(Long id) {
        super("Motorista não encontrado com id: " + id);
    }
}