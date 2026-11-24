package com.ajtbackend.service;

import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.dto.VeiculoRequestDTO;
import com.ajtbackend.dto.VeiculoResponseDTO;
import com.ajtbackend.exception.PlacaJaCadastradaException;
import com.ajtbackend.exception.VeiculoNaoEncontradoException;
import com.ajtbackend.model.Veiculo;
import com.ajtbackend.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

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
        Veiculo veiculo = veiculoRepository.findByPlaca(normalizarPlaca(placa))
                .orElseThrow(() -> new VeiculoNaoEncontradoException(placa));
        return toResponseDTO(veiculo);
    }

    // a validacao aceita "abc-1d23" e "ABC1D23": grava sempre do mesmo jeito (maiusculo, sem hifen),
    // senao a mesma placa entraria duas vezes passando pela checagem de duplicada
    static String normalizarPlaca(String placa) {
        return placa.trim().replace("-", "").toUpperCase(Locale.ROOT);
    }

    @Transactional
    public VeiculoResponseDTO criar(VeiculoRequestDTO dto) {
        String placa = normalizarPlaca(dto.placa());
        if (veiculoRepository.existsByPlaca(placa)) {
            log.warn("Tentativa de cadastro com placa ja existente: {}", placa);
            throw new PlacaJaCadastradaException(placa);
        }

        Veiculo veiculo = Veiculo.builder()
                .label(dto.label())
                .placa(placa)
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

        String placa = normalizarPlaca(dto.placa());
        if (!placa.equals(veiculo.getPlaca()) && veiculoRepository.existsByPlaca(placa)) {
            throw new PlacaJaCadastradaException(placa);
        }

        veiculo.setLabel(dto.label());
        veiculo.setPlaca(placa);
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
            String placa = normalizarPlaca(dto.placa());
            if (!placa.equals(veiculo.getPlaca()) && veiculoRepository.existsByPlaca(placa)) {
                throw new PlacaJaCadastradaException(placa);
            }
            veiculo.setPlaca(placa);
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