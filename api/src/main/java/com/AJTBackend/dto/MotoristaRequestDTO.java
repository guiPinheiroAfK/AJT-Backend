package com.AJTBackend.dto;

import jakarta.validation.constraints.NotBlank;

public record MotoristaRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "CNH é obrigatória")
        String cnh,

        String telefone,
        Double latitudeAtual,
        Double longitudeAtual
) {}