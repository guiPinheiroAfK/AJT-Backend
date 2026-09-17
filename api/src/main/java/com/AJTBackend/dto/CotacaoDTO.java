package com.AJTBackend.dto;

import java.math.BigDecimal;

public record CotacaoDTO(
        String moedaOrigem,
        String moedaDestino,
        BigDecimal taxa,
        String data
) {}
