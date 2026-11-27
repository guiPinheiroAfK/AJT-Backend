package com.ajtbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro feito pela propria pessoa, na tela "Criar conta" (rota publica).
 * Nao tem perfil, ativo nem motorista: quem decide isso e o admin, ao liberar a conta.
 */
public record RegistroRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        @Schema(example = "Maria Souza")
        String nome,

        @NotBlank(message = "Username é obrigatório")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "Username deve ter de 3 a 50 caracteres (letras, números, ponto, _ ou -)")
        @Schema(example = "maria.souza")
        String username,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
        @Schema(example = "senha12345")
        String senha
) {}
