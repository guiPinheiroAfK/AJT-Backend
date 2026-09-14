package com.AJTBackend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record OrdemServicoRequestDTO(
        @NotNull(message = "Data do serviço é obrigatória")
        LocalDate dataServico,

        Long motoristaId,
        Long veiculoId,
        String status
) {}
