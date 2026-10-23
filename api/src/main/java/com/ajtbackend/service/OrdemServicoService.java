package com.ajtbackend.service;

import com.ajtbackend.dto.FiltroOrdemServicoDTO;
import com.ajtbackend.dto.OrdemServicoDoDiaDTO;
import com.ajtbackend.dto.OrdemServicoRequestDTO;
import com.ajtbackend.dto.OrdemServicoResponseDTO;
import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.exception.ConflitoDeVersaoException;
import com.ajtbackend.exception.MotoristaNaoEncontradoException;
import com.ajtbackend.exception.OrdemServicoNaoEncontradoException;
import com.ajtbackend.exception.RegraNegocioException;
import com.ajtbackend.exception.VeiculoNaoEncontradoException;
import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.Veiculo;
import com.ajtbackend.model.enums.StatusOrdemServico;
import com.ajtbackend.repository.Especificacoes;
import com.ajtbackend.repository.MotoristaRepository;
import com.ajtbackend.repository.OrdemServicoRepository;
import com.ajtbackend.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrdemServicoService {

    private static final Logger log = LoggerFactory.getLogger(OrdemServicoService.class);
    private static final StatusOrdemServico STATUS_PADRAO = StatusOrdemServico.ABERTA;
    private static final String TABELA = "ordens_servico";
    private static final String CAMPO_MOTORISTA = "motorista.id";

    private final OrdemServicoRepository ordemServicoRepository;
    private final MotoristaRepository motoristaRepository;
    private final VeiculoRepository veiculoRepository;
    private final AuditoriaService auditoriaService;
    private final AcessoMotorista acessoMotorista;
    private final ParadaOsService paradaOsService;

    // leitura: RF015 (filtros opcionais, somados) e HU07 (o MOTORISTA so ve as OS dele:
    // o motorista do filtro e trocado pelo dele, e sem vinculo ele nao ve nenhuma)
    public PaginaResponseDTO<OrdemServicoResponseDTO> listar(FiltroOrdemServicoDTO filtro, Pageable pageable) {
        Especificacoes.validarPeriodo(filtro.dataInicio(), filtro.dataFim());
        Specification<OrdemServico> spec = Specification.allOf(
                Especificacoes.igual("status", filtro.status()),
                Especificacoes.dataEntre("dataServico", filtro.dataInicio(), filtro.dataFim()),
                Especificacoes.igual(CAMPO_MOTORISTA, acessoMotorista.restrito() ? null : filtro.motoristaId()),
                Especificacoes.igual("veiculo.id", filtro.veiculoId()),
                restricaoDoMotorista());
        return PaginaResponseDTO.de(ordemServicoRepository.findAll(spec, pageable), this::toResponseDTO);
    }

    // motorista que pede uma OS que nao e dele recebe 404: nem fica sabendo que ela existe
    public OrdemServicoResponseDTO buscarPorId(Long id) {
        return toResponseDTO(buscarVisivel(id));
    }

    public PaginaResponseDTO<OrdemServicoResponseDTO> buscarPorStatus(StatusOrdemServico status, Pageable pageable) {
        return listar(new FiltroOrdemServicoDTO(status, null, null, null, null), pageable);
    }

    /**
     * HU07: as OS do motorista logado num dia (hoje, no fuso de Sao Paulo, se nao vier data),
     * cada uma com as paradas na ordem em que ele passa.
     */
    public List<OrdemServicoDoDiaDTO> minhasDoDia(LocalDate data) {
        LocalDate dia = data != null ? data : LocalDate.now(PainelService.FUSO_OPERACAO);
        Long motoristaId = acessoMotorista.motoristaId().orElse(null);
        if (motoristaId == null) {
            return List.of();
        }
        Specification<OrdemServico> spec = Specification.allOf(
                Especificacoes.igual(CAMPO_MOTORISTA, motoristaId),
                Especificacoes.igual("dataServico", dia));
        return ordemServicoRepository.findAll(spec, Sort.by("id")).stream()
                .map(os -> new OrdemServicoDoDiaDTO(toResponseDTO(os), paradaOsService.listarPorOrdemServico(os.getId())))
                .toList();
    }

    /** OS que o usuario logado pode ver (motorista: so as dele); senao 404. */
    OrdemServico buscarVisivel(Long id) {
        OrdemServico ordemServico = ordemServicoRepository.findById(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradoException(id));
        if (!acessoMotorista.podeVer(ordemServico)) {
            throw new OrdemServicoNaoEncontradoException(id);
        }
        return ordemServico;
    }

    private Specification<OrdemServico> restricaoDoMotorista() {
        if (!acessoMotorista.restrito()) {
            return null;
        }
        return acessoMotorista.motoristaId()
                .<Specification<OrdemServico>>map(id -> Especificacoes.igual(CAMPO_MOTORISTA, id))
                .orElseGet(Especificacoes::nenhum);
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
        auditoriaService.registrar(TABELA, salva.getId(), "Ordem de serviço criada");
        log.info("Ordem de serviço criada: id={}", salva.getId());
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

        auditoriaService.registrarAtualizacao(TABELA, id, "Ordem de serviço atualizada", statusAnterior, ordemServico.getStatus());
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

        auditoriaService.registrarAtualizacao(TABELA, id, "Ordem de serviço atualizada", statusAnterior, ordemServico.getStatus());
        ordemServicoRepository.flush(); // grava agora pra resposta ja levar a versao nova
        return toResponseDTO(ordemServico);
    }

    @Transactional
    public void deletar(Long id) {
        if (!ordemServicoRepository.existsById(id)) {
            throw new OrdemServicoNaoEncontradoException(id);
        }
        ordemServicoRepository.deleteById(id);
        auditoriaService.registrar(TABELA, id, "Ordem de serviço removida");
        log.info("Ordem de serviço removida: id={}", id);
    }

    // RNF008: o cliente editou em cima de uma versao velha (outra pessoa salvou no meio tempo)
    private static void conferirVersao(OrdemServico ordemServico, Long versaoDoCliente) {
        if (versaoDoCliente != null && !versaoDoCliente.equals(ordemServico.getVersao())) {
            throw new ConflitoDeVersaoException("Ordem de serviço", ordemServico.getId());
        }
    }

    // a OS agrupa os transfers de um dia (RF009): com transfers dentro, mudar a data deixaria os dois fora de sincronia.
    // novaData nunca e nula aqui: o PUT exige a data (@NotNull) e o PATCH so chama quando ela veio
    private static void conferirMudancaDeData(OrdemServico ordemServico, LocalDate novaData) {
        if (!novaData.equals(ordemServico.getDataServico()) && !ordemServico.getTransfers().isEmpty()) {
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
