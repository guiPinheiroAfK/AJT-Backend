package AJTBackend.exception;

public class UsernameJaCadastradoException extends RuntimeException {
    public UsernameJaCadastradoException(String username) {
        super("Já existe usuário cadastrado com username: " + username);
    }
}