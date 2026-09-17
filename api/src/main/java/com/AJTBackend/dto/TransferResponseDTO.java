package com.AJTBackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record TransferResponseDTO(
        Long id,
        LocalDate dataTransfer,
        LocalTime horaTransfer,
        String origem,
        String destino,
        String status,
        BigDecimal valorBase,
        BigDecimal valorOriginal,
        String moedaOrigem,
        Long osId
) {}
