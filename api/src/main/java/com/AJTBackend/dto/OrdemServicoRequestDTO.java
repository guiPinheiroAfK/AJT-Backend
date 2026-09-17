package com.AJTBackend.dto;

import com.AJTBackend.model.enums.StatusOrdemServico;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record OrdemServicoRequestDTO(
        @NotNull(message = "Data do serviço é obrigatória")
        LocalDate dataServico,

        Long motoristaId,
        Long veiculoId,
        StatusOrdemServico status
) {}
