package com.AJTBackend.service;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.VeiculoRequestDTO;
import com.AJTBackend.dto.VeiculoResponseDTO;
import com.AJTBackend.exception.PlacaJaCadastradaException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VeiculoService {

    private static final Logger log = LoggerFactory.getLogger(VeiculoService.class);

    private final VeiculoRepository veiculoRepository;

    public PaginaResponseDTO<VeiculoResponseDTO> listarTodos(Pageable pageable) {
        return PaginaResponseDTO.de(veiculoRepository.findAll(pageable), this::toResponseDTO);
    }

    public VeiculoResponseDTO buscarPorId(Long id) {
        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(id));
        return toResponseDTO(veiculo);
    }

    public VeiculoResponseDTO buscarPorPlaca(String placa) {
        Veiculo veiculo = veiculoRepository.findByPlaca(placa)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(placa));
        return toResponseDTO(veiculo);
    }

    @Transactional
    public VeiculoResponseDTO criar(VeiculoRequestDTO dto) {
        String placa = dto.placa();
        if (veiculoRepository.existsByPlaca(placa)) {
            log.warn("Tentativa de cadastro com placa ja existente: {}", placa);
            throw new PlacaJaCadastradaException(placa);
        }

        Veiculo veiculo = Veiculo.builder()
                .label(dto.label())
                .placa(dto.placa())
                .capacidade(dto.capacidade())
                .tipo(dto.tipo())
                .marca(dto.marca())
                .build();

        Veiculo salvo = veiculoRepository.save(veiculo);
        log.info("Veiculo criado: id={}, placa={}", salvo.getId(), salvo.getPlaca());
        return toResponseDTO(salvo);
    }

    @Transactional
    public VeiculoResponseDTO atualizar(Long id, VeiculoRequestDTO dto) {
        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(id));
        Versoes.conferir(veiculo.getVersao(), dto.versao(), "Veículo", id);

        if (!dto.placa().equals(veiculo.getPlaca()) && veiculoRepository.existsByPlaca(dto.placa())) {
            throw new PlacaJaCadastradaException(dto.placa());
        }

        veiculo.setLabel(dto.label());
        veiculo.setPlaca(dto.placa());
        veiculo.setCapacidade(dto.capacidade());
        veiculo.setTipo(dto.tipo());
        veiculo.setMarca(dto.marca());

        veiculoRepository.flush(); // grava agora pra resposta ja levar a versao nova
        return toResponseDTO(veiculo);
    }

    @Transactional
    public VeiculoResponseDTO atualizarParcial(Long id, VeiculoRequestDTO dto) {
        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(id));
        Versoes.conferir(veiculo.getVersao(), dto.versao(), "Veículo", id);

        if (dto.label() != null) {
            veiculo.setLabel(dto.label());
        }
        if (dto.placa() != null) {
            if (!dto.placa().equals(veiculo.getPlaca()) && veiculoRepository.existsByPlaca(dto.placa())) {
                throw new PlacaJaCadastradaException(dto.placa());
            }
            veiculo.setPlaca(dto.placa());
        }
        if (dto.capacidade() != null) {
            veiculo.setCapacidade(dto.capacidade());
        }
        if (dto.tipo() != null) {
            veiculo.setTipo(dto.tipo());
        }
        if (dto.marca() != null) {
            veiculo.setMarca(dto.marca());
        }

        veiculoRepository.flush(); // grava agora pra resposta ja levar a versao nova
        return toResponseDTO(veiculo);
    }

    @Transactional
    public void deletar(Long id) {
        if (!veiculoRepository.existsById(id)) {
            throw new VeiculoNaoEncontradoException(id);
        }
        veiculoRepository.deleteById(id);
        log.info("Veiculo removido: id={}", id);
    }

    private VeiculoResponseDTO toResponseDTO(Veiculo veiculo) {
        return new VeiculoResponseDTO(
                veiculo.getId(),
                veiculo.getLabel(),
                veiculo.getPlaca(),
                veiculo.getCapacidade(),
                veiculo.getTipo(),
                veiculo.getMarca(),
                veiculo.getVersao()
        );
    }
}