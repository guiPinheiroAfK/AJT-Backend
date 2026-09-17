package com.AJTBackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record PontoColetaRequestDTO(
        @NotNull(message = "Transfer é obrigatório")
        Long transferId,

        @NotBlank(message = "Local de coleta é obrigatório")
        String localColeta,

        Integer ordemParada,
        LocalTime horarioPrevisto,

        @NotNull(message = "Latitude é obrigatória")
        BigDecimal latitude,

        @NotNull(message = "Longitude é obrigatória")
        BigDecimal longitude
) {}
