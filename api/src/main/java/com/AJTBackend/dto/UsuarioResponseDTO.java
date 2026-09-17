package com.AJTBackend.dto;

import com.AJTBackend.model.enums.Role;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String username,
        Role role,
        Boolean ativo,
        Boolean trocarSenha,
        LocalDateTime ultimoLogin,
        LocalDateTime criadoEm
) {}
