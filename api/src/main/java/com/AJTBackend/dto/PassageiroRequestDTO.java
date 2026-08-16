package com.AJTBackend.dto;

import jakarta.validation.constraints.NotBlank;

public record PassageiroRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "Tipo de documento é obrigatório")
        String tipoDocumento,

        @NotBlank(message = "Documento é obrigatório")
        String documento,

        String nacionalidade
) {}