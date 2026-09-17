package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

import java.math.BigDecimal;
import java.time.LocalTime;

public record PontoColetaRequestDTO(
        @NotNull(message = "Transfer é obrigatório")
        Long transferId,

        @NotBlank(message = "Local de coleta é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Local de coleta não pode ser vazio", groups = OnPatch.class)
        @Size(max = 100, message = "Local de coleta deve ter no máximo 100 caracteres", groups = {Default.class, OnPatch.class})
        String localColeta,

        @Positive(message = "Ordem da parada deve ser maior que zero", groups = {Default.class, OnPatch.class})
        Integer ordemParada,

        LocalTime horarioPrevisto,

        @NotNull(message = "Latitude é obrigatória")
        @DecimalMin(value = "-90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        BigDecimal latitude,

        @NotNull(message = "Longitude é obrigatória")
        @DecimalMin(value = "-180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        BigDecimal longitude
) {}
