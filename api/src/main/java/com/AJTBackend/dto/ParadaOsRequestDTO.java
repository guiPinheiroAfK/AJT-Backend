package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.AcaoParada;
import com.AJTBackend.model.enums.StatusParada;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

import java.time.LocalTime;
import java.util.Set;

public record ParadaOsRequestDTO(
        @NotNull(message = "Ordem de serviço é obrigatória")
        Long osId,

        @NotNull(message = "Ordem da parada é obrigatória")
        @Positive(message = "Ordem da parada deve ser maior que zero", groups = {Default.class, OnPatch.class})
        Integer ordemParada,

        @NotBlank(message = "Local da parada é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Local da parada não pode ser vazio", groups = OnPatch.class)
        @Size(max = 100, message = "Local da parada deve ter no máximo 100 caracteres", groups = {Default.class, OnPatch.class})
        String localParada,

        @DecimalMin(value = "-90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        Double latitude,

        @DecimalMin(value = "-180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        Double longitude,

        LocalTime horarioPrevisto,
        AcaoParada acao,
        StatusParada statusParada,

        @Size(max = 100, message = "Uma parada pode ter no máximo 100 transfers", groups = {Default.class, OnPatch.class})
        Set<Long> transferIds
) {}
