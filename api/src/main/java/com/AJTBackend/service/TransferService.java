package com.AJTBackend.service;

import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String STATUS_PADRAO = "AGUARDANDO_OS";

    private final TransferRepository transferRepository;

    public List<TransferResponseDTO> listarTodos() {
        return transferRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public TransferResponseDTO buscarPorId(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNaoEncontradoException(id));
        return toResponseDTO(transfer);
    }

    public List<TransferResponseDTO> buscarPorStatus(String status) {
        return transferRepository.findByStatus(status)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional
    public TransferResponseDTO criar(TransferRequestDTO dto) {
        Transfer transfer = Transfer.builder()
                .dataTransfer(dto.dataTransfer())
                .horaTransfer(dto.horaTransfer())
                .origem(dto.origem())
                .destino(dto.destino())
                .status(dto.status() != null ? dto.status() : STATUS_PADRAO)
                .valorBase(dto.valorBase())
                .valorOriginal(dto.valorOriginal())
                .moedaOrigem(dto.moedaOrigem())
                .osId(dto.osId())
                .build();

        Transfer salvo = transferRepository.save(transfer);
        log.info("Transfer criado: id={}", salvo.getId());
        return toResponseDTO(salvo);
    }

    @Transactional
    public TransferResponseDTO atualizar(Long id, TransferRequestDTO dto) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNaoEncontradoException(id));

        transfer.setDataTransfer(dto.dataTransfer());
        transfer.setHoraTransfer(dto.horaTransfer());
        transfer.setOrigem(dto.origem());
        transfer.setDestino(dto.destino());
        transfer.setStatus(dto.status() != null ? dto.status() : STATUS_PADRAO);
        transfer.setValorBase(dto.valorBase());
        transfer.setValorOriginal(dto.valorOriginal());
        transfer.setMoedaOrigem(dto.moedaOrigem());
        transfer.setOsId(dto.osId());

        return toResponseDTO(transferRepository.save(transfer));
    }

    @Transactional
    public TransferResponseDTO atualizarParcial(Long id, TransferRequestDTO dto) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNaoEncontradoException(id));

        if (dto.dataTransfer() != null) {
            transfer.setDataTransfer(dto.dataTransfer());
        }
        if (dto.horaTransfer() != null) {
            transfer.setHoraTransfer(dto.horaTransfer());
        }
        if (dto.origem() != null) {
            transfer.setOrigem(dto.origem());
        }
        if (dto.destino() != null) {
            transfer.setDestino(dto.destino());
        }
        if (dto.status() != null) {
            transfer.setStatus(dto.status());
        }
        if (dto.valorBase() != null) {
            transfer.setValorBase(dto.valorBase());
        }
        if (dto.valorOriginal() != null) {
            transfer.setValorOriginal(dto.valorOriginal());
        }
        if (dto.moedaOrigem() != null) {
            transfer.setMoedaOrigem(dto.moedaOrigem());
        }
        if (dto.osId() != null) {
            transfer.setOsId(dto.osId());
        }

        return toResponseDTO(transferRepository.save(transfer));
    }

    @Transactional
    public void deletar(Long id) {
        if (!transferRepository.existsById(id)) {
            throw new TransferNaoEncontradoException(id);
        }
        transferRepository.deleteById(id);
        log.info("Transfer removido: id={}", id);
    }

    private TransferResponseDTO toResponseDTO(Transfer transfer) {
        return new TransferResponseDTO(
                transfer.getId(),
                transfer.getDataTransfer(),
                transfer.getHoraTransfer(),
                transfer.getOrigem(),
                transfer.getDestino(),
                transfer.getStatus(),
                transfer.getValorBase(),
                transfer.getValorOriginal(),
                transfer.getMoedaOrigem(),
                transfer.getOsId()
        );
    }
}
