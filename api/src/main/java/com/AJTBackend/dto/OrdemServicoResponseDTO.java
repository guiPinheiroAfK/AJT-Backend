package com.AJTBackend.dto;

import java.time.LocalDate;

public record OrdemServicoResponseDTO(
        Long id,
        LocalDate dataServico,
        Long motoristaId,
        Long veiculoId,
        String status
) {}
