package com.AJTBackend.dto;

import java.time.LocalTime;
import java.util.Set;

public record ParadaOsResponseDTO(
        Long id,
        Long osId,
        Integer ordemParada,
        String localParada,
        Double latitude,
        Double longitude,
        LocalTime horarioPrevisto,
        String acao,
        String statusParada,
        Set<Long> transferIds
) {}
