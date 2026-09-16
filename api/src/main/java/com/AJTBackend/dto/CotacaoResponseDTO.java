package com.AJTBackend.dto;

import java.util.Map;

public record CotacaoResponseDTO(
        Double amount,
        String base,
        String date,
        Map<String, Double> rates
) {}
