package com.ajtbackend.dto;

import com.ajtbackend.model.enums.StatusTransfer;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** filtros opcionais de GET /api/transfers (RF015). campo vazio = nao filtra; todos juntos = E. */
public record FiltroTransferDTO(
        StatusTransfer status,
        @Schema(description = "Data do transfer a partir de (inclusive)", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataInicio,
        @Schema(description = "Data do transfer até (inclusive)", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataFim,
        @Schema(description = "Parte da origem ou do destino", example = "aeroporto")
        String busca,
        @Schema(description = "Só os transfers desta ordem de serviço", example = "2")
        Long osId,
        @Schema(description = "true = só os transfers sem ordem de serviço", example = "true")
        Boolean semOs
) {}
