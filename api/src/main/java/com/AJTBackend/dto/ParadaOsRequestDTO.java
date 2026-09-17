package com.AJTBackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.Set;

public record ParadaOsRequestDTO(
        @NotNull(message = "Ordem de serviço é obrigatória")
        Long osId,

        @NotNull(message = "Ordem da parada é obrigatória")
        Integer ordemParada,

        @NotBlank(message = "Local da parada é obrigatório")
        String localParada,

        Double latitude,
        Double longitude,
        LocalTime horarioPrevisto,
        String acao,
        String statusParada,
        Set<Long> transferIds
) {}
