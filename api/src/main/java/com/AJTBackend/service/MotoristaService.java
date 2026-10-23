package com.AJTBackend.service;

import com.AJTBackend.dto.FiltroMotoristaDTO;
import com.AJTBackend.dto.MotoristaRequestDTO;
import com.AJTBackend.dto.MotoristaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.exception.CnhJaCadastradaException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.repository.Especificacoes;
import com.AJTBackend.repository.MotoristaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MotoristaService {

    private static final Logger log = LoggerFactory.getLogger(MotoristaService.class);

    private final MotoristaRepository motoristaRepository;

    // RF015: nome e cnh sao opcionais (contem, sem diferenciar maiusculas)
    public PaginaResponseDTO<MotoristaResponseDTO> listar(FiltroMotoristaDTO filtro, Pageable pageable) {
        Specification<Motorista> spec = Specification.allOf(
                Especificacoes.contem("nome", filtro.nome()),
                Especificacoes.contem("cnh", filtro.cnh()));
        return PaginaResponseDTO.de(motoristaRepository.findAll(spec, pageable), this::toResponseDTO);
    }

    public MotoristaResponseDTO buscarPorId(Long id) {
        Motorista motorista = motoristaRepository.findById(id)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(id));
        return toResponseDTO(motorista);
    }

    public MotoristaResponseDTO buscarPorCnh(String cnh) {
        Motorista motorista = motoristaRepository.findByCnh(cnh)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(cnh));
        return toResponseDTO(motorista);
    }

    @Transactional
    public MotoristaResponseDTO criar(MotoristaRequestDTO dto) {
        String cnh = dto.cnh();
        if (motoristaRepository.existsByCnh(cnh)) {
            log.warn("Tentativa de cadastro com CNH ja existente: {}", cnh);
            throw new CnhJaCadastradaException(cnh);
        }

        Motorista motorista = Motorista.builder()
                .nome(dto.nome())
                .cnh(dto.cnh())
                .telefone(dto.telefone())
                .latitudeAtual(dto.latitudeAtual())
                .longitudeAtual(dto.longitudeAtual())
                .build();

        Motorista salvo = motoristaRepository.save(motorista);
        log.info("Motorista criado: id={}, cnh={}", salvo.getId(), salvo.getCnh());
        return toResponseDTO(salvo);
    }

    @Transactional
    public MotoristaResponseDTO atualizar(Long id, MotoristaRequestDTO dto) {
        Motorista motorista = motoristaRepository.findById(id)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(id));
        Versoes.conferir(motorista.getVersao(), dto.versao(), "Motorista", id);

        if (!dto.cnh().equals(motorista.getCnh()) && motoristaRepository.existsByCnh(dto.cnh())) {
            throw new CnhJaCadastradaException(dto.cnh());
        }

        motorista.setNome(dto.nome());
        motorista.setCnh(dto.cnh());
        motorista.setTelefone(dto.telefone());
        motorista.setLatitudeAtual(dto.latitudeAtual());
        motorista.setLongitudeAtual(dto.longitudeAtual());

        motoristaRepository.flush(); // grava agora pra resposta ja levar a versao nova
        return toResponseDTO(motorista);
    }

    @Transactional
    public MotoristaResponseDTO atualizarParcial(Long id, MotoristaRequestDTO dto) {
        Motorista motorista = motoristaRepository.findById(id)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(id));
        Versoes.conferir(motorista.getVersao(), dto.versao(), "Motorista", id);

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

        motoristaRepository.flush(); // grava agora pra resposta ja levar a versao nova
        return toResponseDTO(motorista);
    }

    @Transactional
    public void deletar(Long id) {
        if (!motoristaRepository.existsById(id)) {
            throw new MotoristaNaoEncontradoException(id);
        }
        motoristaRepository.deleteById(id);
        log.info("Motorista removido: id={}", id);
    }

    private MotoristaResponseDTO toResponseDTO(Motorista motorista) {
        return new MotoristaResponseDTO(
                motorista.getId(),
                motorista.getNome(),
                motorista.getCnh(),
                motorista.getTelefone(),
                motorista.getLatitudeAtual(),
                motorista.getLongitudeAtual(),
                motorista.getVersao()
        );
    }
}