package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

public record UsuarioRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Nome não pode ser vazio", groups = OnPatch.class)
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres", groups = {Default.class, OnPatch.class})
        @Schema(example = "Maria Souza")
        String nome,

        @NotBlank(message = "Username é obrigatório")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "Username deve ter de 3 a 50 caracteres (letras, números, ponto, _ ou -)", groups = {Default.class, OnPatch.class})
        @Schema(example = "maria.souza")
        String username,

        // obrigatoria no create (validado no service); opcional no update
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres", groups = {Default.class, OnPatch.class})
        @Schema(example = "senha12345")
        String senha,

        @NotNull(message = "Role é obrigatória")
        Role role,

        @Schema(example = "true")
        Boolean ativo
) {}
