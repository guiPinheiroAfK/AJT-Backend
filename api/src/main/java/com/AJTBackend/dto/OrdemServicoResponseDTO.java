package com.AJTBackend.dto;

import com.AJTBackend.model.enums.StatusOrdemServico;

import java.time.LocalDate;

public record OrdemServicoResponseDTO(
        Long id,
        LocalDate dataServico,
        Long motoristaId,
        Long veiculoId,
        StatusOrdemServico status
) {}
