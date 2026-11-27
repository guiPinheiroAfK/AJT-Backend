package com.ajtbackend.dto;

public record PassageiroResponseDTO(
        Long id,
        String nome,
        String tipoDocumento,
        String documento,
        String nacionalidade,
        Long versao
) {}
