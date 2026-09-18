package com.AJTBackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank(message = "Username é obrigatório")
        @Size(max = 50, message = "Username deve ter no máximo 50 caracteres")
        @Schema(example = "admin")
        String username,

        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
        @Schema(example = "admin123")
        String senha
) {}
