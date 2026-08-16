package com.AJTBackend.service;

import com.AJTBackend.dto.MotoristaRequestDTO;
import com.AJTBackend.dto.MotoristaResponseDTO;
import com.AJTBackend.exception.CnhJaCadastradaException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.repository.MotoristaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MotoristaService {

    private final MotoristaRepository motoristaRepository;

    public List<MotoristaResponseDTO> listarTodos() {
        return motoristaRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public MotoristaResponseDTO buscarPorId(Long id) {
        Motorista motorista = motoristaRepository.findById(id)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(id));
        return toResponseDTO(motorista);
    }

    public MotoristaResponseDTO buscarPorCnh(String cnh) {
        Motorista motorista = motoristaRepository.findByCnh(cnh)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(0L));
        return toResponseDTO(motorista);
    }

    public MotoristaResponseDTO criar(MotoristaRequestDTO dto) {
        if (motoristaRepository.existsByCnh(dto.cnh())) {
            throw new CnhJaCadastradaException(dto.cnh());
        }

        Motorista motorista = Motorista.builder()
                .nome(dto.nome())
                .cnh(dto.cnh())
                .telefone(dto.telefone())
                .latitudeAtual(dto.latitudeAtual())
                .longitudeAtual(dto.longitudeAtual())
                .build();

        return toResponseDTO(motoristaRepository.save(motorista));
    }

    public MotoristaResponseDTO atualizar(Long id, MotoristaRequestDTO dto) {
        Motorista motorista = motoristaRepository.findById(id)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(id));

        motorista.setNome(dto.nome());
        motorista.setCnh(dto.cnh());
        motorista.setTelefone(dto.telefone());
        motorista.setLatitudeAtual(dto.latitudeAtual());
        motorista.setLongitudeAtual(dto.longitudeAtual());

        return toResponseDTO(motoristaRepository.save(motorista));
    }

    public MotoristaResponseDTO atualizarParcial(Long id, MotoristaRequestDTO dto) {
        Motorista motorista = motoristaRepository.findById(id)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(id));

        if (dto.nome() != null) {
            motorista.setNome(dto.nome());
        }
        if (dto.cnh() != null) {
            if (!dto.cnh().equals(motorista.getCnh()) && motoristaRepository.existsByCnh(dto.cnh())) {
                throw new CnhJaCadastradaException(dto.cnh());
            }
            motorista.setCnh(dto.cnh());
        }
        if (dto.telefone() != null) {
            motorista.setTelefone(dto.telefone());
        }
        if (dto.latitudeAtual() != null) {
            motorista.setLatitudeAtual(dto.latitudeAtual());
        }
        if (dto.longitudeAtual() != null) {
            motorista.setLongitudeAtual(dto.longitudeAtual());
        }

        return toResponseDTO(motoristaRepository.save(motorista));
    }

    public void deletar(Long id) {
        if (!motoristaRepository.existsById(id)) {
            throw new MotoristaNaoEncontradoException(id);
        }
        motoristaRepository.deleteById(id);
    }

    private MotoristaResponseDTO toResponseDTO(Motorista motorista) {
        return new MotoristaResponseDTO(
                motorista.getId(),
                motorista.getNome(),
                motorista.getCnh(),
                motorista.getTelefone(),
                motorista.getLatitudeAtual(),
                motorista.getLongitudeAtual()
        );
    }
}