package com.ajtbackend.dto;

import com.ajtbackend.model.enums.AcaoParada;
import com.ajtbackend.model.enums.StatusParada;

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
        Set<Long> transferIds,
        Long versao
) {}
