package com.ajtbackend.dto;

import java.math.BigDecimal;

public record CotacaoDTO(
        String moedaOrigem,
        String moedaDestino,
        BigDecimal taxa,
        String data
) {}
