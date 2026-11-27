package com.ajtbackend.dto;

import com.ajtbackend.dto.validacao.OnPatch;
import com.ajtbackend.model.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

public record UsuarioRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Pattern(regexp = "\\s*\\S.*", message = "Nome não pode ser vazio", groups = OnPatch.class)
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
        Boolean ativo,

        // HU07: cadastro de motorista ligado ao usuario (so perfil MOTORISTA). se nao vier, o PUT
        // desliga e o PATCH mantem. trocar o perfil pra outro que nao MOTORISTA tambem desliga
        @Schema(example = "1")
        Long motoristaId,

        // versao que o cliente carregou (RNF008); se vier e estiver velha, a API responde 409
        @Schema(example = "3")
        Long versao
) {}
