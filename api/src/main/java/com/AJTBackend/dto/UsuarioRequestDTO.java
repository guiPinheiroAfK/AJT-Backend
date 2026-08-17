package com.AJTBackend.dto;

import jakarta.validation.constraints.NotBlank;

public record UsuarioRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "Username é obrigatório")
        String username,

        String senha, // opcional no update (mantem senha atual se nao vier); obrigatorio no create fica a cargo do service

        @NotBlank(message = "Role é obrigatória")
        String role
) {}