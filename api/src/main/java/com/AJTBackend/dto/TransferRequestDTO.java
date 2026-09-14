package com.AJTBackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record TransferRequestDTO(
        @NotNull(message = "Data do transfer é obrigatória")
        LocalDate dataTransfer,

        @NotNull(message = "Hora do transfer é obrigatória")
        LocalTime horaTransfer,

        @NotBlank(message = "Origem é obrigatória")
        String origem,

        @NotBlank(message = "Destino é obrigatório")
        String destino,

        String status,
        BigDecimal valorBase,
        BigDecimal valorOriginal,
        String moedaOrigem,
        Long osId
) {}
