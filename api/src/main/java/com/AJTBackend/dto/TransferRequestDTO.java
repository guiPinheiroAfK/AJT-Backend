package com.AJTBackend.dto;

import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.StatusTransfer;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record TransferRequestDTO(
        @NotNull(message = "Data do transfer é obrigatória")
        LocalDate dataTransfer,

        @NotNull(message = "Hora do transfer é obrigatória")
        LocalTime horaTransfer,

        @NotBlank(message = "Origem é obrigatória")
        @Pattern(regexp = ".*\\S.*", message = "Origem não pode ser vazia", groups = OnPatch.class)
        @Size(max = 100, message = "Origem deve ter no máximo 100 caracteres", groups = {Default.class, OnPatch.class})
        String origem,

        @NotBlank(message = "Destino é obrigatório")
        @Pattern(regexp = ".*\\S.*", message = "Destino não pode ser vazio", groups = OnPatch.class)
        @Size(max = 100, message = "Destino deve ter no máximo 100 caracteres", groups = {Default.class, OnPatch.class})
        String destino,

        StatusTransfer status,

        @PositiveOrZero(message = "Valor base não pode ser negativo", groups = {Default.class, OnPatch.class})
        @Digits(integer = 8, fraction = 2, message = "Valor base deve ter até 8 dígitos e 2 casas decimais", groups = {Default.class, OnPatch.class})
        BigDecimal valorBase,

        @PositiveOrZero(message = "Valor original não pode ser negativo", groups = {Default.class, OnPatch.class})
        @Digits(integer = 8, fraction = 2, message = "Valor original deve ter até 8 dígitos e 2 casas decimais", groups = {Default.class, OnPatch.class})
        BigDecimal valorOriginal,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Moeda deve ser um código ISO de 3 letras (ex: USD)", groups = {Default.class, OnPatch.class})
        String moedaOrigem,

        Long osId
) {}
