package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

public record VeiculoRequestDTO(
        @NotBlank(message = "Label é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Label não pode ser vazio", groups = OnPatch.class)
        @Size(max = 50, message = "Label deve ter no máximo 50 caracteres", groups = {Default.class, OnPatch.class})
        String label,

        @NotBlank(message = "Placa é obrigatória")
        @Pattern(regexp = ".*\\S.*", message = "Placa não pode ser vazia", groups = OnPatch.class)
        @Size(max = 10, message = "Placa deve ter no máximo 10 caracteres", groups = {Default.class, OnPatch.class})
        String placa,

        @NotNull(message = "Capacidade é obrigatória")
        @Positive(message = "Capacidade deve ser maior que zero", groups = {Default.class, OnPatch.class})
        Integer capacidade,

        @Size(max = 50, message = "Tipo deve ter no máximo 50 caracteres", groups = {Default.class, OnPatch.class})
        String tipo,

        @Size(max = 50, message = "Marca deve ter no máximo 50 caracteres", groups = {Default.class, OnPatch.class})
        String marca
) {}
