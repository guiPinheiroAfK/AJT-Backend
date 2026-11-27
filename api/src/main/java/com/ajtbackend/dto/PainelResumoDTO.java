package com.ajtbackend.dto;

import com.ajtbackend.model.enums.StatusOrdemServico;
import com.ajtbackend.model.enums.StatusTransfer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * indicadores do painel (HU08, RF012), todos de um dia so.
 * os mapas por status sempre trazem todos os status (com 0 quando nao ha nada).
 * faturamento vem null pra quem nao e ADMIN nem GERENTE.
 */
public record PainelResumoDTO(
        @Schema(example = "2026-10-19")
        LocalDate data,
        long totalTransfers,
        Map<StatusTransfer, Long> transfersPorStatus,
        long totalOrdensServico,
        Map<StatusOrdemServico, Long> ordensServicoPorStatus,
        @Schema(description = "Soma do valor em BRL dos transfers do dia, sem os cancelados. Só ADMIN e GERENTE.", example = "1250.00")
        BigDecimal faturamentoDia,
        @Schema(description = "Mesma soma, do dia 1 do mês até a data. Só ADMIN e GERENTE.", example = "18400.00")
        BigDecimal faturamentoMes
) {}
