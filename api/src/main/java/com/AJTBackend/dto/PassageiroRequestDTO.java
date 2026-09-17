package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

public record PassageiroRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Nome não pode ser vazio", groups = OnPatch.class)
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres", groups = {Default.class, OnPatch.class})
        String nome,

        @NotBlank(message = "Tipo de documento é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Tipo de documento não pode ser vazio", groups = OnPatch.class)
        @Size(max = 20, message = "Tipo de documento deve ter no máximo 20 caracteres", groups = {Default.class, OnPatch.class})
        String tipoDocumento,

        @NotBlank(message = "Documento é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Documento não pode ser vazio", groups = OnPatch.class)
        @Size(max = 50, message = "Documento deve ter no máximo 50 caracteres", groups = {Default.class, OnPatch.class})
        String documento,

        @Size(max = 50, message = "Nacionalidade deve ter no máximo 50 caracteres", groups = {Default.class, OnPatch.class})
        String nacionalidade
) {}
