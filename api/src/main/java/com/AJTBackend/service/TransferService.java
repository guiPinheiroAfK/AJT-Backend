package com.AJTBackend.service;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.exception.CotacaoIndisponivelException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.repository.OrdemServicoRepository;
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
import java.math.RoundingMode;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String MOEDA_PADRAO = "BRL";

    private final TransferRepository transferRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final CotacaoService cotacaoService;
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
        String moeda = normalizarMoeda(dto.moedaOrigem());
        BigDecimal valorBase = calcularValorBase(dto.valorBase(), dto.valorOriginal(), moeda);

        return transactionTemplate.execute(tx -> {
            validarOrdemServico(dto.osId());

            Transfer transfer = Transfer.builder()
                    .dataTransfer(dto.dataTransfer())
                    .horaTransfer(dto.horaTransfer())
                    .origem(dto.origem())
                    .destino(dto.destino())
                    .status(dto.status() != null ? dto.status() : StatusTransfer.AGUARDANDO_OS)
                    .valorBase(valorBase)
                    .valorOriginal(dto.valorOriginal())
                    .moedaOrigem(moeda)
                    .osId(dto.osId())
                    .build();

            Transfer salvo = transferRepository.save(transfer);
            log.info("Transfer criado: id={}", salvo.getId());
            return toResponseDTO(salvo);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransferResponseDTO atualizar(Long id, TransferRequestDTO dto) {
        String moeda = normalizarMoeda(dto.moedaOrigem());
        BigDecimal valorBase = calcularValorBase(dto.valorBase(), dto.valorOriginal(), moeda);

        return transactionTemplate.execute(tx -> {
            Transfer transfer = transferRepository.findById(id)
                    .orElseThrow(() -> new TransferNaoEncontradoException(id));
            validarOrdemServico(dto.osId());

            transfer.setDataTransfer(dto.dataTransfer());
            transfer.setHoraTransfer(dto.horaTransfer());
            transfer.setOrigem(dto.origem());
            transfer.setDestino(dto.destino());
            transfer.setStatus(dto.status() != null ? dto.status() : StatusTransfer.AGUARDANDO_OS);
            transfer.setValorBase(valorBase);
            transfer.setValorOriginal(dto.valorOriginal());
            transfer.setMoedaOrigem(moeda);
            transfer.setOsId(dto.osId());

            return toResponseDTO(transfer);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransferResponseDTO atualizarParcial(Long id, TransferRequestDTO dto) {
        String moedaDto = normalizarMoeda(dto.moedaOrigem());

        // so consulta a cotacao se o valor em moeda estrangeira mudou e o valor base nao veio explicito
        BigDecimal valorBaseRecalculado = null;
        if (dto.valorBase() == null && (dto.valorOriginal() != null || moedaDto != null)) {
            Transfer atual = transactionTemplate.execute(tx -> transferRepository.findById(id)
                    .orElseThrow(() -> new TransferNaoEncontradoException(id)));
            BigDecimal valorOriginal = dto.valorOriginal() != null ? dto.valorOriginal() : atual.getValorOriginal();
            String moeda = moedaDto != null ? moedaDto : atual.getMoedaOrigem();
            valorBaseRecalculado = calcularValorBase(null, valorOriginal, moeda);
        }
        BigDecimal valorBaseFinal = dto.valorBase() != null ? dto.valorBase() : valorBaseRecalculado;

        return transactionTemplate.execute(tx -> {
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
                validarOrdemServico(dto.osId());
                transfer.setOsId(dto.osId());
            }

            return toResponseDTO(transfer);
        });
    }

    @Transactional
    public void deletar(Long id) {
        if (!transferRepository.existsById(id)) {
            throw new TransferNaoEncontradoException(id);
        }
        transferRepository.deleteById(id);
        log.info("Transfer removido: id={}", id);
    }

    private void validarOrdemServico(Long osId) {
        if (osId != null && !ordemServicoRepository.existsById(osId)) {
            throw new OrdemServicoNaoEncontradoException(osId);
        }
    }

    /**
     * Se valorBase ja foi informado explicitamente, respeita ele. Caso
     * contrario, se houver valorOriginal numa moeda estrangeira, converte
     * pra BRL usando a cotacao atual (via Feign/Frankfurter API, com cache).
     */
    private BigDecimal calcularValorBase(BigDecimal valorBase, BigDecimal valorOriginal, String moedaOrigem) {
        if (valorBase != null) {
            return valorBase;
        }
        if (valorOriginal == null || moedaOrigem == null || moedaOrigem.equals(MOEDA_PADRAO)) {
            return valorOriginal;
        }
        try {
            BigDecimal taxa = cotacaoService.obterCotacao(moedaOrigem, MOEDA_PADRAO).taxa();
            return valorOriginal.multiply(taxa).setScale(2, RoundingMode.HALF_UP);
        } catch (CotacaoIndisponivelException e) {
            // nao trava o cadastro por causa de uma dependencia externa fora do ar
            log.warn("Cotacao indisponivel ({} -> {}), valor base nao convertido automaticamente: {}",
                    moedaOrigem, MOEDA_PADRAO, e.getMessage());
            return null;
        }
    }

    private static String normalizarMoeda(String moeda) {
        return moeda == null ? null : moeda.toUpperCase(Locale.ROOT);
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
