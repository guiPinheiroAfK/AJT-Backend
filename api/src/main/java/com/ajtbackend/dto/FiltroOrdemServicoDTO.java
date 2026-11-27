package com.ajtbackend.dto;

import com.ajtbackend.model.enums.StatusOrdemServico;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * filtros opcionais de GET /api/ordens-servico (RF015). campo vazio = nao filtra; todos juntos = E.
 * pro MOTORISTA o motoristaId e sempre o dele (HU07), o que vier aqui e ignorado.
 */
public record FiltroOrdemServicoDTO(
        StatusOrdemServico status,
        @Schema(description = "Data do serviço a partir de (inclusive)", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataInicio,
        @Schema(description = "Data do serviço até (inclusive)", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataFim,
        @Schema(example = "1")
        Long motoristaId,
        @Schema(example = "1")
        Long veiculoId
) {}
