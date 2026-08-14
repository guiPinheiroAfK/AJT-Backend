package AJTBackend.dto;

public record VeiculoRequestDTO(
        String label,
        String placa,
        Integer capacidade,
        String tipo,
        String marca
) {}