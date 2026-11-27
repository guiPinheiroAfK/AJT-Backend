package com.ajtbackend.dto;

import com.ajtbackend.model.enums.StatusOrdemServico;
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
        StatusOrdemServico status,

        // versao que o cliente carregou (RNF008); se vier e estiver velha, a API responde 409
        @Schema(example = "3")
        Long versao
) {}
