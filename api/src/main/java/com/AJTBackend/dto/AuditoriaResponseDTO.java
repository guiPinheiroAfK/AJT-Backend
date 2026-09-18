package com.AJTBackend.dto;

import java.time.LocalDateTime;

public record AuditoriaResponseDTO(
        Long id,
        String tabelaAfetada,
        Long registroId,
        String mensagem,
        LocalDateTime dataHora
) {}
