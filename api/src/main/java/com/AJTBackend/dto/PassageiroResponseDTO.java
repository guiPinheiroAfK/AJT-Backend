package com.AJTBackend.dto;

public record PassageiroResponseDTO(
        Long id,
        String nome,
        String tipoDocumento,
        String documento,
        String nacionalidade
) {}