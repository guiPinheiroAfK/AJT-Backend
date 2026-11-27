package com.ajtbackend.dto;

import java.util.List;

/**
 * uma OS do dia do motorista (HU07), ja com as paradas na ordem em que ele passa,
 * pra tela "minhas OS" nao precisar de uma chamada por OS.
 */
public record OrdemServicoDoDiaDTO(
        OrdemServicoResponseDTO ordem,
        List<ParadaOsResponseDTO> paradas
) {}
