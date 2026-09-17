package com.AJTBackend.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Formato estavel de paginacao pro front (o JSON do PageImpl do Spring
 * nao tem contrato garantido entre versoes).
 */
public record PaginaResponseDTO<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas,
        boolean primeira,
        boolean ultima
) {
    public static <E, T> PaginaResponseDTO<T> de(Page<E> page, Function<E, T> mapper) {
        return new PaginaResponseDTO<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
