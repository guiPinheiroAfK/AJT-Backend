package com.AJTBackend.service;

import com.AJTBackend.dto.OrdemServicoRequestDTO;
import com.AJTBackend.dto.OrdemServicoResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrdemServicoService {

    private static final Logger log = LoggerFactory.getLogger(OrdemServicoService.class);
    private static final StatusOrdemServico STATUS_PADRAO = StatusOrdemServico.ABERTA;
    private static final String TABELA = "ordens_servico";

    private final OrdemServicoRepository ordemServicoRepository;
    private final MotoristaRepository motoristaRepository;
    private final VeiculoRepository veiculoRepository;
    private final AuditoriaService auditoriaService;

    public PaginaResponseDTO<OrdemServicoResponseDTO> listarTodos(Pageable pageable) {
        return PaginaResponseDTO.de(ordemServicoRepository.findAll(pageable), this::toResponseDTO);
    }

    public OrdemServicoResponseDTO buscarPorId(Long id) {
        OrdemServico ordemServico = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(id));
        return toResponseDTO(ordemServico);
    }

    public PaginaResponseDTO<OrdemServicoResponseDTO> buscarPorStatus(StatusOrdemServico status, Pageable pageable) {
        return PaginaResponseDTO.de(ordemServicoRepository.findByStatus(status, pageable), this::toResponseDTO);
    }

    @Transactional
    public OrdemServicoResponseDTO criar(OrdemServicoRequestDTO dto) {
        OrdemServico ordemServico = OrdemServico.builder()
                .dataServico(dto.dataServico())
                .motorista(buscarMotorista(dto.motoristaId()))
                .veiculo(buscarVeiculo(dto.veiculoId()))
                .status(dto.status() != null ? dto.status() : STATUS_PADRAO)
                .build();

        OrdemServico salva = ordemServicoRepository.save(ordemServico);
        auditoriaService.registrar(TABELA, salva.getId(), "Ordem de servico criada");
        log.info("Ordem de servico criada: id={}", salva.getId());
        return toResponseDTO(salva);
    }

    @Transactional
    public OrdemServicoResponseDTO atualizar(Long id, OrdemServicoRequestDTO dto) {
        OrdemServico ordemServico = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(id));
        conferirVersao(ordemServico, dto.versao());
        conferirMudancaDeData(ordemServico, dto.dataServico());

        ordemServico.setDataServico(dto.dataServico());
        ordemServico.setMotorista(buscarMotorista(dto.motoristaId()));
        ordemServico.setVeiculo(buscarVeiculo(dto.veiculoId()));
        StatusOrdemServico statusAnterior = ordemServico.getStatus();
        ordemServico.setStatus(dto.status() != null ? dto.status() : STATUS_PADRAO);

        auditoriaService.registrarAtualizacao(TABELA, id, "Ordem de servico atualizada", statusAnterior, ordemServico.getStatus());
        ordemServicoRepository.flush(); // grava agora pra resposta ja levar a versao nova
        return toResponseDTO(ordemServico);
    }

    @Transactional
    public OrdemServicoResponseDTO atualizarParcial(Long id, OrdemServicoRequestDTO dto) {
        OrdemServico ordemServico = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(id));
        conferirVersao(ordemServico, dto.versao());

        if (dto.dataServico() != null) {
            conferirMudancaDeData(ordemServico, dto.dataServico());
            ordemServico.setDataServico(dto.dataServico());
        }
        if (dto.motoristaId() != null) {
            ordemServico.setMotorista(buscarMotorista(dto.motoristaId()));
        }
        if (dto.veiculoId() != null) {
            ordemServico.setVeiculo(buscarVeiculo(dto.veiculoId()));
        }
        StatusOrdemServico statusAnterior = ordemServico.getStatus();
        if (dto.status() != null) {
            ordemServico.setStatus(dto.status());
        }

        auditoriaService.registrarAtualizacao(TABELA, id, "Ordem de servico atualizada", statusAnterior, ordemServico.getStatus());
        ordemServicoRepository.flush(); // grava agora pra resposta ja levar a versao nova
        return toResponseDTO(ordemServico);
    }

    @Transactional
    public void deletar(Long id) {
        if (!ordemServicoRepository.existsById(id)) {
            throw new OrdemServicoNaoEncontradoException(id);
        }
        ordemServicoRepository.deleteById(id);
        auditoriaService.registrar(TABELA, id, "Ordem de servico removida");
        log.info("Ordem de servico removida: id={}", id);
    }

    // RNF008: o cliente editou em cima de uma versao velha (outra pessoa salvou no meio tempo)
    private static void conferirVersao(OrdemServico ordemServico, Long versaoDoCliente) {
        if (versaoDoCliente != null && !versaoDoCliente.equals(ordemServico.getVersao())) {
            throw new ConflitoDeVersaoException("Ordem de serviço", ordemServico.getId());
        }
    }

    // a OS agrupa os transfers de um dia (RF009): com transfers dentro, mudar a data deixaria os dois fora de sincronia
    private static void conferirMudancaDeData(OrdemServico ordemServico, LocalDate novaData) {
        if (novaData != null && !novaData.equals(ordemServico.getDataServico()) && !ordemServico.getTransfers().isEmpty()) {
            throw new RegraNegocioException("A ordem de serviço #" + ordemServico.getId() + " tem "
                    + ordemServico.getTransfers().size() + " transfer(s) do dia dela. Desvincule-os antes de mudar a data.");
        }
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
                ordemServico.getStatus(),
                ordemServico.getVersao()
        );
    }
}
