package com.ajtbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** filtros opcionais de GET /api/motoristas (RF015). campo vazio = nao filtra. */
public record FiltroMotoristaDTO(
        @Schema(description = "Parte do nome, sem diferenciar maiúsculas", example = "carlos")
        String nome,
        @Schema(description = "Parte da CNH", example = "1234")
        String cnh
) {}
