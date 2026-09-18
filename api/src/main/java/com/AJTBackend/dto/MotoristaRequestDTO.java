package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(example = "Carlos Andrade")
        String nome,

        @NotBlank(message = "CNH é obrigatória")
        @Pattern(regexp = ".*\\S.*", message = "CNH não pode ser vazia", groups = OnPatch.class)
        @Size(max = 20, message = "CNH deve ter no máximo 20 caracteres", groups = {Default.class, OnPatch.class})
        @Schema(example = "12345678901")
        String cnh,

        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres", groups = {Default.class, OnPatch.class})
        @Schema(example = "(11) 98888-7777")
        String telefone,

        @DecimalMin(value = "-90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "90", message = "Latitude inválida", groups = {Default.class, OnPatch.class})
        @Schema(example = "-23.5505")
        Double latitudeAtual,

        @DecimalMin(value = "-180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        @DecimalMax(value = "180", message = "Longitude inválida", groups = {Default.class, OnPatch.class})
        @Schema(example = "-46.6333")
        Double longitudeAtual
) {}
