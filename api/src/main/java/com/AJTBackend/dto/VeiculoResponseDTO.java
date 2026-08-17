package com.AJTBackend.dto;

public record VeiculoResponseDTO(
        Long id,
        String label,
        String placa,
        Integer capacidade,
        String tipo,
        String marca
) {}