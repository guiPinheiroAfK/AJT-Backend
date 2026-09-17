package com.AJTBackend.dto;

import com.AJTBackend.model.enums.AcaoParada;
import com.AJTBackend.model.enums.StatusParada;

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
        AcaoParada acao,
        StatusParada statusParada,
        Set<Long> transferIds
) {}
