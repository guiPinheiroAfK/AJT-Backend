package com.AJTBackend.service;

import com.AJTBackend.dto.ParadaOsRequestDTO;
import com.AJTBackend.dto.ParadaOsResponseDTO;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.ParadaOsNaoEncontradaException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.AJTBackend.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ParadaOsService {

    private static final String STATUS_PADRAO = "PENDENTE";

    private final ParadaOsRepository paradaOsRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final TransferRepository transferRepository;

    public List<ParadaOsResponseDTO> listarTodos() {
        return paradaOsRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public ParadaOsResponseDTO buscarPorId(Long id) {
        ParadaOs paradaOs = paradaOsRepository.findById(id)
                .orElseThrow(() -> new ParadaOsNaoEncontradaException(id));
        return toResponseDTO(paradaOs);
    }

    public List<ParadaOsResponseDTO> listarPorOrdemServico(Long osId) {
        return paradaOsRepository.findByOrdemServicoIdOrderByOrdemParadaAsc(osId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public ParadaOsResponseDTO criar(ParadaOsRequestDTO dto) {
        ParadaOs paradaOs = ParadaOs.builder()
                .ordemServico(buscarOrdemServico(dto.osId()))
                .ordemParada(dto.ordemParada())
                .localParada(dto.localParada())
                .latitude(dto.latitude())
                .longitude(dto.longitude())
                .horarioPrevisto(dto.horarioPrevisto())
                .acao(dto.acao())
                .statusParada(dto.statusParada() != null ? dto.statusParada() : STATUS_PADRAO)
                .transfers(buscarTransfers(dto.transferIds()))
                .build();

        return toResponseDTO(paradaOsRepository.save(paradaOs));
    }

    public ParadaOsResponseDTO atualizar(Long id, ParadaOsRequestDTO dto) {
        ParadaOs paradaOs = paradaOsRepository.findById(id)
                .orElseThrow(() -> new ParadaOsNaoEncontradaException(id));

        paradaOs.setOrdemServico(buscarOrdemServico(dto.osId()));
        paradaOs.setOrdemParada(dto.ordemParada());
        paradaOs.setLocalParada(dto.localParada());
        paradaOs.setLatitude(dto.latitude());
        paradaOs.setLongitude(dto.longitude());
        paradaOs.setHorarioPrevisto(dto.horarioPrevisto());
        paradaOs.setAcao(dto.acao());
        paradaOs.setStatusParada(dto.statusParada() != null ? dto.statusParada() : STATUS_PADRAO);
        paradaOs.setTransfers(buscarTransfers(dto.transferIds()));

        return toResponseDTO(paradaOsRepository.save(paradaOs));
    }

    public ParadaOsResponseDTO atualizarParcial(Long id, ParadaOsRequestDTO dto) {
        ParadaOs paradaOs = paradaOsRepository.findById(id)
                .orElseThrow(() -> new ParadaOsNaoEncontradaException(id));

        if (dto.osId() != null) {
            paradaOs.setOrdemServico(buscarOrdemServico(dto.osId()));
        }
        if (dto.ordemParada() != null) {
            paradaOs.setOrdemParada(dto.ordemParada());
        }
        if (dto.localParada() != null) {
            paradaOs.setLocalParada(dto.localParada());
        }
        if (dto.latitude() != null) {
            paradaOs.setLatitude(dto.latitude());
        }
        if (dto.longitude() != null) {
            paradaOs.setLongitude(dto.longitude());
        }
        if (dto.horarioPrevisto() != null) {
            paradaOs.setHorarioPrevisto(dto.horarioPrevisto());
        }
        if (dto.acao() != null) {
            paradaOs.setAcao(dto.acao());
        }
        if (dto.statusParada() != null) {
            paradaOs.setStatusParada(dto.statusParada());
        }
        if (dto.transferIds() != null) {
            paradaOs.setTransfers(buscarTransfers(dto.transferIds()));
        }

        return toResponseDTO(paradaOsRepository.save(paradaOs));
    }

    public void deletar(Long id) {
        if (!paradaOsRepository.existsById(id)) {
            throw new ParadaOsNaoEncontradaException(id);
        }
        paradaOsRepository.deleteById(id);
    }

    private OrdemServico buscarOrdemServico(Long osId) {
        return ordemServicoRepository.findById(osId)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(osId));
    }

    private Set<Transfer> buscarTransfers(Set<Long> transferIds) {
        if (transferIds == null || transferIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Transfer> transfers = new HashSet<>();
        for (Long transferId : transferIds) {
            transfers.add(transferRepository.findById(transferId)
                    .orElseThrow(() -> new TransferNaoEncontradoException(transferId)));
        }
        return transfers;
    }

    private ParadaOsResponseDTO toResponseDTO(ParadaOs paradaOs) {
        Set<Long> transferIds = paradaOs.getTransfers()
                .stream()
                .map(Transfer::getId)
                .collect(java.util.stream.Collectors.toSet());

        return new ParadaOsResponseDTO(
                paradaOs.getId(),
                paradaOs.getOrdemServico().getId(),
                paradaOs.getOrdemParada(),
                paradaOs.getLocalParada(),
                paradaOs.getLatitude(),
                paradaOs.getLongitude(),
                paradaOs.getHorarioPrevisto(),
                paradaOs.getAcao(),
                paradaOs.getStatusParada(),
                transferIds
        );
    }
}
