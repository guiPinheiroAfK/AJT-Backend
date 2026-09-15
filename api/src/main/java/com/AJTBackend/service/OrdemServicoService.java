package com.AJTBackend.service;

import com.AJTBackend.dto.OrdemServicoRequestDTO;
import com.AJTBackend.dto.OrdemServicoResponseDTO;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrdemServicoService {

    private static final String STATUS_PADRAO = "ABERTA";

    private final OrdemServicoRepository ordemServicoRepository;
    private final MotoristaRepository motoristaRepository;
    private final VeiculoRepository veiculoRepository;

    public List<OrdemServicoResponseDTO> listarTodos() {
        return ordemServicoRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public OrdemServicoResponseDTO buscarPorId(Long id) {
        OrdemServico ordemServico = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(id));
        return toResponseDTO(ordemServico);
    }

    public List<OrdemServicoResponseDTO> buscarPorStatus(String status) {
        return ordemServicoRepository.findByStatus(status)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional
    public OrdemServicoResponseDTO criar(OrdemServicoRequestDTO dto) {
        OrdemServico ordemServico = OrdemServico.builder()
                .dataServico(dto.dataServico())
                .motorista(buscarMotorista(dto.motoristaId()))
                .veiculo(buscarVeiculo(dto.veiculoId()))
                .status(dto.status() != null ? dto.status() : STATUS_PADRAO)
                .build();

        return toResponseDTO(ordemServicoRepository.save(ordemServico));
    }

    @Transactional
    public OrdemServicoResponseDTO atualizar(Long id, OrdemServicoRequestDTO dto) {
        OrdemServico ordemServico = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(id));

        ordemServico.setDataServico(dto.dataServico());
        ordemServico.setMotorista(buscarMotorista(dto.motoristaId()));
        ordemServico.setVeiculo(buscarVeiculo(dto.veiculoId()));
        ordemServico.setStatus(dto.status() != null ? dto.status() : STATUS_PADRAO);

        return toResponseDTO(ordemServicoRepository.save(ordemServico));
    }

    @Transactional
    public OrdemServicoResponseDTO atualizarParcial(Long id, OrdemServicoRequestDTO dto) {
        OrdemServico ordemServico = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(id));

        if (dto.dataServico() != null) {
            ordemServico.setDataServico(dto.dataServico());
        }
        if (dto.motoristaId() != null) {
            ordemServico.setMotorista(buscarMotorista(dto.motoristaId()));
        }
        if (dto.veiculoId() != null) {
            ordemServico.setVeiculo(buscarVeiculo(dto.veiculoId()));
        }
        if (dto.status() != null) {
            ordemServico.setStatus(dto.status());
        }

        return toResponseDTO(ordemServicoRepository.save(ordemServico));
    }

    @Transactional
    public void deletar(Long id) {
        if (!ordemServicoRepository.existsById(id)) {
            throw new OrdemServicoNaoEncontradoException(id);
        }
        ordemServicoRepository.deleteById(id);
    }

    private Motorista buscarMotorista(Long motoristaId) {
        if (motoristaId == null) {
            return null;
        }
        return motoristaRepository.findById(motoristaId)
                .orElseThrow(() -> new MotoristaNaoEncontradoException(motoristaId));
    }

    private Veiculo buscarVeiculo(Long veiculoId) {
        if (veiculoId == null) {
            return null;
        }
        return veiculoRepository.findById(veiculoId)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(veiculoId));
    }

    private OrdemServicoResponseDTO toResponseDTO(OrdemServico ordemServico) {
        return new OrdemServicoResponseDTO(
                ordemServico.getId(),
                ordemServico.getDataServico(),
                ordemServico.getMotorista() != null ? ordemServico.getMotorista().getId() : null,
                ordemServico.getVeiculo() != null ? ordemServico.getVeiculo().getId() : null,
                ordemServico.getStatus()
        );
    }
}
