package com.AJTBackend.dto;

import java.math.BigDecimal;
import java.util.Map;

// usa BigDecimal (e nao Double) pra nao perder precisao na taxa de cambio
public record CotacaoResponseDTO(
        BigDecimal amount,
        String base,
        String date,
        Map<String, BigDecimal> rates
) {}
