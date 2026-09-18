package com.AJTBackend.service;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Passageiro;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.PassageiroRepository;
import com.AJTBackend.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String TABELA = "transfers";

    private final TransferRepository transferRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final PassageiroRepository passageiroRepository;
    private final ValorTransferService valorTransferService;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transactionTemplate;

    public PaginaResponseDTO<TransferResponseDTO> listarTodos(Pageable pageable) {
        return PaginaResponseDTO.de(transferRepository.findAll(pageable), this::toResponseDTO);
    }

    public TransferResponseDTO buscarPorId(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNaoEncontradoException(id));
        return toResponseDTO(transfer);
    }

    public PaginaResponseDTO<TransferResponseDTO> buscarPorStatus(StatusTransfer status, Pageable pageable) {
        return PaginaResponseDTO.de(transferRepository.findByStatus(status, pageable), this::toResponseDTO);
    }

    /*
     * os metodos de escrita abaixo nao rodam dentro de uma transacao unica:
     * a cotacao (chamada HTTP de ate alguns segundos) e feita antes, e so a
     * gravacao usa transacao (TransactionTemplate). Assim nao prendemos uma
     * conexao do pool do banco esperando a API externa.
     */

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransferResponseDTO criar(TransferRequestDTO dto) {
        String moeda = ValorTransferService.normalizarMoeda(dto.moedaOrigem());
        BigDecimal valorBase = valorTransferService.calcularValorBase(dto.valorBase(), dto.valorOriginal(), moeda);

        return transactionTemplate.execute(tx -> {
            Transfer transfer = Transfer.builder()
                    .dataTransfer(dto.dataTransfer())
                    .horaTransfer(dto.horaTransfer())
                    .origem(dto.origem())
                    .destino(dto.destino())
                    .status(dto.status() != null ? dto.status() : StatusTransfer.AGUARDANDO_OS)
                    .valorBase(valorBase)
                    .valorOriginal(dto.valorOriginal())
                    .moedaOrigem(moeda)
                    .ordemServico(buscarOrdemServico(dto.osId()))
                    .passageiros(buscarPassageiros(dto.passageiroIds()))
                    .build();

            Transfer salvo = transferRepository.save(transfer);
            auditoriaService.registrar(TABELA, salvo.getId(),
                    "Transfer criado: " + salvo.getOrigem() + " -> " + salvo.getDestino());
            log.info("Transfer criado: id={}", salvo.getId());
            return toResponseDTO(salvo);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransferResponseDTO atualizar(Long id, TransferRequestDTO dto) {
        String moeda = ValorTransferService.normalizarMoeda(dto.moedaOrigem());
        BigDecimal valorBase = valorTransferService.calcularValorBase(dto.valorBase(), dto.valorOriginal(), moeda);

        return transactionTemplate.execute(tx -> {
            Transfer transfer = transferRepository.findById(id)
                    .orElseThrow(() -> new TransferNaoEncontradoException(id));
            StatusTransfer statusAnterior = transfer.getStatus();

            transfer.setDataTransfer(dto.dataTransfer());
            transfer.setHoraTransfer(dto.horaTransfer());
            transfer.setOrigem(dto.origem());
            transfer.setDestino(dto.destino());
            transfer.setStatus(dto.status() != null ? dto.status() : StatusTransfer.AGUARDANDO_OS);
            transfer.setValorBase(valorBase);
            transfer.setValorOriginal(dto.valorOriginal());
            transfer.setMoedaOrigem(moeda);
            transfer.setOrdemServico(buscarOrdemServico(dto.osId()));
            // PUT sem passageiroIds mantem os atuais; enviar [] remove todos
            if (dto.passageiroIds() != null) {
                transfer.setPassageiros(buscarPassageiros(dto.passageiroIds()));
            }

            auditoriaService.registrarAtualizacao(TABELA, id, "Transfer atualizado", statusAnterior, transfer.getStatus());
            return toResponseDTO(transfer);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransferResponseDTO atualizarParcial(Long id, TransferRequestDTO dto) {
        String moedaDto = ValorTransferService.normalizarMoeda(dto.moedaOrigem());

        // so consulta a cotacao se o valor em moeda estrangeira mudou e o valor base nao veio explicito
        BigDecimal valorBaseRecalculado = null;
        if (dto.valorBase() == null && (dto.valorOriginal() != null || moedaDto != null)) {
            Transfer atual = transactionTemplate.execute(tx -> transferRepository.findById(id)
                    .orElseThrow(() -> new TransferNaoEncontradoException(id)));
            BigDecimal valorOriginal = dto.valorOriginal() != null ? dto.valorOriginal() : atual.getValorOriginal();
            String moeda = moedaDto != null ? moedaDto : atual.getMoedaOrigem();
            valorBaseRecalculado = valorTransferService.calcularValorBase(null, valorOriginal, moeda);
        }
        BigDecimal valorBaseFinal = dto.valorBase() != null ? dto.valorBase() : valorBaseRecalculado;

        return transactionTemplate.execute(tx -> {
            Transfer transfer = transferRepository.findById(id)
                    .orElseThrow(() -> new TransferNaoEncontradoException(id));
            StatusTransfer statusAnterior = transfer.getStatus();

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
            if (dto.valorOriginal() != null) {
                transfer.setValorOriginal(dto.valorOriginal());
            }
            if (moedaDto != null) {
                transfer.setMoedaOrigem(moedaDto);
            }
            if (valorBaseFinal != null) {
                transfer.setValorBase(valorBaseFinal);
            }
            if (dto.osId() != null) {
                transfer.setOrdemServico(buscarOrdemServico(dto.osId()));
            }
            if (dto.passageiroIds() != null) {
                transfer.setPassageiros(buscarPassageiros(dto.passageiroIds()));
            }

            auditoriaService.registrarAtualizacao(TABELA, id, "Transfer atualizado", statusAnterior, transfer.getStatus());
            return toResponseDTO(transfer);
        });
    }

    @Transactional
    public void deletar(Long id) {
        if (!transferRepository.existsById(id)) {
            throw new TransferNaoEncontradoException(id);
        }
        transferRepository.deleteById(id);
        auditoriaService.registrar(TABELA, id, "Transfer removido");
        log.info("Transfer removido: id={}", id);
    }

    // sem osId o transfer fica "aguardando OS"; com osId, a OS precisa existir
    private OrdemServico buscarOrdemServico(Long osId) {
        if (osId == null) {
            return null;
        }
        return ordemServicoRepository.findById(osId)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(osId));
    }

    // uma unica query (findAllById); se algum id nao voltou, ele nao existe
    private Set<Passageiro> buscarPassageiros(Set<Long> passageiroIds) {
        if (passageiroIds == null || passageiroIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Passageiro> encontrados = passageiroRepository.findAllById(passageiroIds);
        if (encontrados.size() != passageiroIds.size()) {
            Set<Long> idsEncontrados = encontrados.stream().map(Passageiro::getId).collect(Collectors.toSet());
            Long faltando = passageiroIds.stream().filter(pid -> !idsEncontrados.contains(pid)).findFirst().orElseThrow();
            throw new PassageiroNaoEncontradoException(faltando);
        }
        return new HashSet<>(encontrados);
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
                transfer.getOrdemServico() != null ? transfer.getOrdemServico().getId() : null,
                transfer.getPassageiros().stream().map(Passageiro::getId).collect(Collectors.toSet())
        );
    }
}
