package com.AJTBackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaSenhaRequestDTO(
        @NotBlank(message = "Senha atual é obrigatória")
        @Schema(example = "admin123")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, max = 72, message = "Nova senha deve ter entre 8 e 72 caracteres")
        @Schema(example = "NovaSenha123")
        String novaSenha
) {}
