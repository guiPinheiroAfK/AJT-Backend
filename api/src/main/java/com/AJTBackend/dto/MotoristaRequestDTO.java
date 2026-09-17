package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

public record MotoristaRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Nome não pode ser vazio", groups = OnPatch.class)
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres", groups = {Default.class, OnPatch.class})
        String nome,

        @NotBlank(message = "CNH é obrigatória")
        @Pattern(regexp = ".*\\S.*", message = "CNH não pode ser vazia", groups = OnPatch.class)
        @Size(max = 20, message = "CNH deve ter no máximo 20 caracteres", groups = {Default.class, OnPatch.class})
        String cnh,

        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres", groups = {Default.class, OnPatch.class})
        String telefone,

        @DecimalMin(value = "-90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        Double latitudeAtual,

        @DecimalMin(value = "-180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        Double longitudeAtual
) {}
