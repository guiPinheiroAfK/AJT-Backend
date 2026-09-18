package com.AJTBackend.dto;

import com.AJTBackend.model.enums.StatusOrdemServico;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record OrdemServicoRequestDTO(
        @NotNull(message = "Data do serviço é obrigatória")
        @Schema(example = "2026-09-25")
        LocalDate dataServico,

        @Schema(example = "1")
        Long motoristaId,
        @Schema(example = "1")
        Long veiculoId,
        StatusOrdemServico status
) {}
