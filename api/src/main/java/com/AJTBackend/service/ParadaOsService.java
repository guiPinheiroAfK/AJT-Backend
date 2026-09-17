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
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.model.enums.StatusParada;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParadaOsService {

    private static final Logger log = LoggerFactory.getLogger(ParadaOsService.class);
    private static final StatusParada STATUS_PADRAO = StatusParada.PENDENTE;

    private final ParadaOsRepository paradaOsRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final TransferRepository transferRepository;

    public PaginaResponseDTO<ParadaOsResponseDTO> listarTodos(Pageable pageable) {
        // transfers de cada parada sao carregados em lote (hibernate.default_batch_fetch_size)
        return PaginaResponseDTO.de(paradaOsRepository.findAll(pageable), this::toResponseDTO);
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

    @Transactional
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

        ParadaOs salva = paradaOsRepository.save(paradaOs);
        log.info("Parada de OS criada: id={}, osId={}", salva.getId(), dto.osId());
        return toResponseDTO(salva);
    }

    @Transactional
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

        return toResponseDTO(paradaOs);
    }

    @Transactional
    public ParadaOsResponseDTO atualizarParcial(Long id, ParadaOsRequestDTO dto) {
        validarAlteracaoPorMotorista(dto);

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

        return toResponseDTO(paradaOs);
    }

    @Transactional
    public void deletar(Long id) {
        if (!paradaOsRepository.existsById(id)) {
            throw new ParadaOsNaoEncontradaException(id);
        }
        paradaOsRepository.deleteById(id);
        log.info("Parada de OS removida: id={}", id);
    }

    private OrdemServico buscarOrdemServico(Long osId) {
        return ordemServicoRepository.findById(osId)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(osId));
    }

    private Set<Transfer> buscarTransfers(Set<Long> transferIds) {
        if (transferIds == null || transferIds.isEmpty()) {
            return new HashSet<>();
        }

        // uma unica query (antes era um findById por transfer)
        List<Transfer> encontrados = transferRepository.findAllById(transferIds);
        if (encontrados.size() != transferIds.size()) {
            Set<Long> idsEncontrados = encontrados.stream().map(Transfer::getId).collect(Collectors.toSet());
            Long faltando = transferIds.stream().filter(id -> !idsEncontrados.contains(id)).findFirst().orElseThrow();
            throw new TransferNaoEncontradoException(faltando);
        }
        return new HashSet<>(encontrados);
    }

    /**
     * motorista so pode atualizar o andamento da parada (statusParada);
     * o SecurityConfig libera o PATCH pra ele, e aqui restringimos os campos.
     */
    private void validarAlteracaoPorMotorista(ParadaOsRequestDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean ehMotorista = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.MOTORISTA.name()));
        if (!ehMotorista) {
            return;
        }

        boolean alteraOutrosCampos = dto.osId() != null || dto.ordemParada() != null || dto.localParada() != null
                || dto.latitude() != null || dto.longitude() != null || dto.horarioPrevisto() != null
                || dto.acao() != null || dto.transferIds() != null;
        if (alteraOutrosCampos) {
            throw new AccessDeniedException("Motorista só pode alterar o status da parada");
        }
    }

    private ParadaOsResponseDTO toResponseDTO(ParadaOs paradaOs) {
        Set<Long> transferIds = paradaOs.getTransfers()
                .stream()
                .map(Transfer::getId)
                .collect(Collectors.toSet());

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
