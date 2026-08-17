package com.AJTBackend.dto;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String username,
        String role,
        Boolean ativo,
        LocalDateTime ultimoLogin,
        LocalDateTime criadoEm
) {}