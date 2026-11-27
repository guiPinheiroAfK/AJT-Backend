package com.ajtbackend.dto;

import com.ajtbackend.model.enums.StatusOrdemServico;

import java.time.LocalDate;

public record OrdemServicoResponseDTO(
        Long id,
        LocalDate dataServico,
        Long motoristaId,
        Long veiculoId,
        StatusOrdemServico status,
        Long versao
) {}
