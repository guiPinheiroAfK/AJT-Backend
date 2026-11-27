package com.ajtbackend.dto;

import com.ajtbackend.model.enums.StatusTransfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record TransferResponseDTO(
        Long id,
        LocalDate dataTransfer,
        LocalTime horaTransfer,
        String origem,
        String destino,
        StatusTransfer status,
        BigDecimal valorBase,
        BigDecimal valorOriginal,
        String moedaOrigem,
        Long osId,
        Set<Long> passageiroIds,
        Long versao
) {}
