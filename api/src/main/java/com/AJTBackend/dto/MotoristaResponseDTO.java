package com.AJTBackend.dto;

public record MotoristaResponseDTO(
        Long id,
        String nome,
        String cnh,
        String telefone,
        Double latitudeAtual,
        Double longitudeAtual
) {}