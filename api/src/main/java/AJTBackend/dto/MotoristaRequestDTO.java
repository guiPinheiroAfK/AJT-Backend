package AJTBackend.dto;

public record MotoristaRequestDTO(
        String nome,
        String cnh,
        String telefone,
        Double latitudeAtual,
        Double longitudeAtual
) {}