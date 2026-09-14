package com.AJTBackend.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

public record PontoColetaResponseDTO(
        Long id,
        Long transferId,
        String localColeta,
        Integer ordemParada,
        LocalTime horarioPrevisto,
        BigDecimal latitude,
        BigDecimal longitude
) {}
