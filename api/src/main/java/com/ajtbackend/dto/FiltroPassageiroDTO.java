package com.ajtbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * filtros opcionais de GET /api/passageiros (RF015). campo vazio = nao filtra.
 * o documento e cifrado no banco (RNF007), por isso nao entra como filtro.
 */
public record FiltroPassageiroDTO(
        @Schema(description = "Parte do nome, sem diferenciar maiúsculas", example = "ana")
        String nome,
        @Schema(description = "Parte da nacionalidade", example = "brasil")
        String nacionalidade
) {}
