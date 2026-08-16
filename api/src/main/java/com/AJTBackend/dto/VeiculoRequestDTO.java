package com.AJTBackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VeiculoRequestDTO(
        @NotBlank(message = "Label é obrigatório")
        String label,

        @NotBlank(message = "Placa é obrigatória")
        String placa,

        @NotNull(message = "Capacidade é obrigatória")
        @Positive(message = "Capacidade deve ser maior que zero")
        Integer capacidade,

        String tipo,
        String marca
) {}