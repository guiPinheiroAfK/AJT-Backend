package com.AJTBackend.service;

import com.AJTBackend.dto.PontoColetaRequestDTO;
import com.AJTBackend.dto.PontoColetaResponseDTO;
import com.AJTBackend.exception.PontoColetaNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.PontoColeta;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.repository.PontoColetaRepository;
import com.AJTBackend.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PontoColetaService {

    private final PontoColetaRepository pontoColetaRepository;
    private final TransferRepository transferRepository;

    public List<PontoColetaResponseDTO> listarTodos() {
        return pontoColetaRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PontoColetaResponseDTO buscarPorId(Long id) {
        PontoColeta pontoColeta = pontoColetaRepository.findById(id)
                .orElseThrow(() -> new PontoColetaNaoEncontradoException(id));
        return toResponseDTO(pontoColeta);
    }

    public List<PontoColetaResponseDTO> listarPorTransfer(Long transferId) {
        return pontoColetaRepository.findByTransferIdOrderByOrdemParadaAsc(transferId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public PontoColetaResponseDTO criar(PontoColetaRequestDTO dto) {
        Transfer transfer = buscarTransfer(dto.transferId());

        PontoColeta pontoColeta = PontoColeta.builder()
                .transfer(transfer)
                .localColeta(dto.localColeta())
                .ordemParada(dto.ordemParada())
                .horarioPrevisto(dto.horarioPrevisto())
                .latitude(dto.latitude())
                .longitude(dto.longitude())
                .build();

        return toResponseDTO(pontoColetaRepository.save(pontoColeta));
    }

    public PontoColetaResponseDTO atualizar(Long id, PontoColetaRequestDTO dto) {
        PontoColeta pontoColeta = pontoColetaRepository.findById(id)
                .orElseThrow(() -> new PontoColetaNaoEncontradoException(id));

        pontoColeta.setTransfer(buscarTransfer(dto.transferId()));
        pontoColeta.setLocalColeta(dto.localColeta());
        pontoColeta.setOrdemParada(dto.ordemParada());
        pontoColeta.setHorarioPrevisto(dto.horarioPrevisto());
        pontoColeta.setLatitude(dto.latitude());
        pontoColeta.setLongitude(dto.longitude());

        return toResponseDTO(pontoColetaRepository.save(pontoColeta));
    }

    public PontoColetaResponseDTO atualizarParcial(Long id, PontoColetaRequestDTO dto) {
        PontoColeta pontoColeta = pontoColetaRepository.findById(id)
                .orElseThrow(() -> new PontoColetaNaoEncontradoException(id));

        if (dto.transferId() != null) {
            pontoColeta.setTransfer(buscarTransfer(dto.transferId()));
        }
        if (dto.localColeta() != null) {
            pontoColeta.setLocalColeta(dto.localColeta());
        }
        if (dto.ordemParada() != null) {
            pontoColeta.setOrdemParada(dto.ordemParada());
        }
        if (dto.horarioPrevisto() != null) {
            pontoColeta.setHorarioPrevisto(dto.horarioPrevisto());
        }
        if (dto.latitude() != null) {
            pontoColeta.setLatitude(dto.latitude());
        }
        if (dto.longitude() != null) {
            pontoColeta.setLongitude(dto.longitude());
        }

        return toResponseDTO(pontoColetaRepository.save(pontoColeta));
    }

    public void deletar(Long id) {
        if (!pontoColetaRepository.existsById(id)) {
            throw new PontoColetaNaoEncontradoException(id);
        }
        pontoColetaRepository.deleteById(id);
    }

    private Transfer buscarTransfer(Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNaoEncontradoException(transferId));
    }

    private PontoColetaResponseDTO toResponseDTO(PontoColeta pontoColeta) {
        return new PontoColetaResponseDTO(
                pontoColeta.getId(),
                pontoColeta.getTransfer().getId(),
                pontoColeta.getLocalColeta(),
                pontoColeta.getOrdemParada(),
                pontoColeta.getHorarioPrevisto(),
                pontoColeta.getLatitude(),
                pontoColeta.getLongitude()
        );
    }
}
