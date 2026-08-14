package AJTBackend.dto;

public record UsuarioRequestDTO(
        String nome,
        String username,
        String senha,
        String role
) {}