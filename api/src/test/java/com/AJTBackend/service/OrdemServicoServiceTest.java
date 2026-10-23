package com.AJTBackend.service;

import com.AJTBackend.dto.FiltroOrdemServicoDTO;
import com.AJTBackend.dto.OrdemServicoDoDiaDTO;
import com.AJTBackend.dto.OrdemServicoRequestDTO;
import com.AJTBackend.dto.OrdemServicoResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.ParadaOsResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.VeiculoNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.Veiculo;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.VeiculoRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/*
 * o que testa: as regras do OrdemServicoService — periodo invertido no filtro (400), motorista
 * sem vinculo nao ve nenhuma OS e o com vinculo so ve as dele (404 nas outras, HU07), "minhas
 * OS do dia", motorista/veiculo inexistentes (404), versao velha (409), a OS com transfers nao
 * muda de dia (400), status padrao ABERTA, PATCH so mexendo no que veio, exclusao de quem nao
 * existe e a auditoria de cada escrita.
 *
 * como rodar: teste unitario com repositorios, AcessoMotorista e ParadaOsService mockados.
 * sem spring, sem banco. "mvn test".
 *
 * por que existe: a OS e o centro da operacao do dia; regra errada aqui manda motorista pra
 * rota errada ou esconde OS de quem precisa dela.
 */
class OrdemServicoServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 21);

    private final OrdemServicoRepository repository = mock(OrdemServicoRepository.class);
    private final MotoristaRepository motoristaRepository = mock(MotoristaRepository.class);
    private final VeiculoRepository veiculoRepository = mock(VeiculoRepository.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final AcessoMotorista acessoMotorista = mock(AcessoMotorista.class);
    private final ParadaOsService paradaOsService = mock(ParadaOsService.class);
    private final OrdemServicoService service = new OrdemServicoService(repository, motoristaRepository, veiculoRepository,
            auditoriaService, acessoMotorista, paradaOsService);

    private static OrdemServico os(Long id, Long motoristaId) {
        return OrdemServico.builder().id(id).versao(1L).dataServico(DIA)
                .motorista(motoristaId != null ? Motorista.builder().id(motoristaId).build() : null).build();
    }

    private static OrdemServico osComTransfer() {
        OrdemServico comTransfer = os(1L, null);
        comTransfer.setTransfers(new ArrayList<>(List.of(Transfer.builder().id(2L).build())));
        return comTransfer;
    }

    private static OrdemServicoRequestDTO dto(LocalDate data, Long motoristaId, Long veiculoId, StatusOrdemServico status, Long versao) {
        return new OrdemServicoRequestDTO(data, motoristaId, veiculoId, status, versao);
    }

    private static FiltroOrdemServicoDTO filtro(LocalDate inicio, LocalDate fim) {
        return new FiltroOrdemServicoDTO(null, inicio, fim, null, null);
    }

    @SuppressWarnings("unchecked")
    private void repositorioDevolve(OrdemServico os) {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(os), PageRequest.of(0, 5), 1));
    }

    // executa a Specification que o service mandou pro repositorio e devolve o CriteriaBuilder usado
    @SuppressWarnings("unchecked")
    private CriteriaBuilder filtroAplicadoNaConsulta() {
        ArgumentCaptor<Specification<OrdemServico>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(spec.capture(), any(Pageable.class));
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
        spec.getValue().toPredicate(mock(Root.class, RETURNS_MOCKS), mock(CriteriaQuery.class), cb);
        return cb;
    }

    // ---------------- leitura ----------------

    @Test
    @SuppressWarnings("unchecked")
    void periodoInvertidoLanca400SemConsultar() {
        // Given
        FiltroOrdemServicoDTO invertido = filtro(DIA, DIA.minusDays(1));
        PageRequest pagina = PageRequest.of(0, 5);

        // When / Then
        assertThatThrownBy(() -> service.listar(invertido, pagina))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("depois da final");
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void gerenteListaComOFiltroDeMotoristaQueEscolheu() {
        // Given
        when(acessoMotorista.restrito()).thenReturn(false);
        repositorioDevolve(os(1L, 7L));
        FiltroOrdemServicoDTO doMotorista7 = new FiltroOrdemServicoDTO(StatusOrdemServico.ABERTA, DIA, DIA, 7L, 2L);

        // When
        PaginaResponseDTO<OrdemServicoResponseDTO> pagina = service.listar(doMotorista7, PageRequest.of(0, 5));

        // Then
        assertThat(pagina.conteudo()).extracting(OrdemServicoResponseDTO::motoristaId).containsExactly(7L);
        verify(filtroAplicadoNaConsulta()).equal(any(), eq(7L));
    }

    @Test
    void motoristaListaSoAsDeleMesmoPedindoAsDeOutro() {
        // Given
        when(acessoMotorista.restrito()).thenReturn(true);
        when(acessoMotorista.motoristaId()).thenReturn(Optional.of(5L));
        repositorioDevolve(os(1L, 5L));
        FiltroOrdemServicoDTO pedindoOMotorista7 = new FiltroOrdemServicoDTO(null, null, null, 7L, null);

        // When
        service.listar(pedindoOMotorista7, PageRequest.of(0, 5));

        // Then
        CriteriaBuilder cb = filtroAplicadoNaConsulta();
        verify(cb).equal(any(), eq(5L));
        verify(cb, never()).equal(any(), eq(7L));
    }

    @Test
    void motoristaSemCadastroLigadoRecebeFiltroQueNaoTrazNada() {
        // Given
        when(acessoMotorista.restrito()).thenReturn(true);
        when(acessoMotorista.motoristaId()).thenReturn(Optional.empty());
        repositorioDevolve(os(1L, 5L));

        // When
        service.listar(filtro(null, null), PageRequest.of(0, 5));

        // Then
        verify(filtroAplicadoNaConsulta()).disjunction();
    }

    @Test
    void buscaPorStatusUsaOFiltroDeStatus() {
        // Given
        when(acessoMotorista.restrito()).thenReturn(false);
        repositorioDevolve(os(1L, 5L));

        // When
        PaginaResponseDTO<OrdemServicoResponseDTO> pagina = service.buscarPorStatus(StatusOrdemServico.ABERTA, PageRequest.of(0, 5));

        // Then
        assertThat(pagina.conteudo()).hasSize(1);
        verify(filtroAplicadoNaConsulta()).equal(any(), eq(StatusOrdemServico.ABERTA));
    }

    @Test
    void buscaPorIdVisivelDevolveAOs() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, 5L)));
        when(acessoMotorista.podeVer(any())).thenReturn(true);

        // When
        OrdemServicoResponseDTO encontrada = service.buscarPorId(1L);

        // Then
        assertThat(encontrada.id()).isEqualTo(1L);
        assertThat(encontrada.motoristaId()).isEqualTo(5L);
    }

    @Test
    void motoristaNaoVeOsDeOutroERecebe404() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, 6L)));
        when(acessoMotorista.podeVer(any())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(1L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void osInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void minhasDoDiaSemCadastroLigadoVemVaziaSemConsultar() {
        // Given
        when(acessoMotorista.motoristaId()).thenReturn(Optional.empty());

        // When
        List<OrdemServicoDoDiaDTO> minhas = service.minhasDoDia(DIA);

        // Then
        assertThat(minhas).isEmpty();
        verify(repository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void minhasDoDiaTrazAsOsDoMotoristaComAsParadas() {
        // Given
        when(acessoMotorista.motoristaId()).thenReturn(Optional.of(5L));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(os(1L, 5L)));
        when(paradaOsService.listarPorOrdemServico(1L)).thenReturn(List.of(
                new ParadaOsResponseDTO(3L, 1L, 1, "Aeroporto", null, null, null, null, null, Set.of(), 0L)));

        // When
        List<OrdemServicoDoDiaDTO> minhas = service.minhasDoDia(null); // sem data = hoje

        // Then
        assertThat(minhas).singleElement().satisfies(doDia -> {
            assertThat(doDia.ordem().id()).isEqualTo(1L);
            assertThat(doDia.paradas()).extracting(ParadaOsResponseDTO::localParada).containsExactly("Aeroporto");
        });
    }

    // ---------------- escrita ----------------

    @Test
    void cadastroComMotoristaInexistenteLanca404SemGravar() {
        // Given
        when(motoristaRepository.findById(5L)).thenReturn(Optional.empty());
        OrdemServicoRequestDTO dto = dto(DIA, 5L, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto)).isInstanceOf(MotoristaNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroComVeiculoInexistenteLanca404SemGravar() {
        // Given
        when(veiculoRepository.findById(6L)).thenReturn(Optional.empty());
        OrdemServicoRequestDTO dto = dto(DIA, null, 6L, null, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto)).isInstanceOf(VeiculoNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroSemStatusNasceAbertaEAudita() {
        // Given
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).build()));
        when(veiculoRepository.findById(6L)).thenReturn(Optional.of(Veiculo.builder().id(6L).build()));
        when(repository.save(any())).thenAnswer(inv -> {
            OrdemServico salva = inv.getArgument(0);
            salva.setId(10L);
            return salva;
        });

        // When
        OrdemServicoResponseDTO criada = service.criar(dto(DIA, 5L, 6L, null, null));

        // Then
        assertThat(criada.status()).isEqualTo(StatusOrdemServico.ABERTA);
        assertThat(criada.motoristaId()).isEqualTo(5L);
        assertThat(criada.veiculoId()).isEqualTo(6L);
        verify(auditoriaService).registrar("ordens_servico", 10L, "Ordem de serviço criada");
    }

    @Test
    void putDeOsInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        OrdemServicoRequestDTO dto = dto(DIA, null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void patchDeOsInexistenteLanca404() {
        // Given
        when(repository.findById(9L)).thenReturn(Optional.empty());
        OrdemServicoRequestDTO dto = dto(null, null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void putComVersaoVelhaLanca409() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));
        OrdemServicoRequestDTO versaoVelha = dto(DIA, null, null, null, 0L);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void patchComVersaoVelhaLanca409() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));
        OrdemServicoRequestDTO versaoVelha = dto(null, null, null, null, 0L);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void osSemTransfersPodeMudarDeDia() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));

        // When
        OrdemServicoResponseDTO salva = service.atualizar(1L, dto(DIA.plusDays(3), null, null, null, null));

        // Then
        assertThat(salva.dataServico()).isEqualTo(DIA.plusDays(3));
    }

    @Test
    void osComTransfersNaoMudaDeDiaNoPut() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(osComTransfer()));
        OrdemServicoRequestDTO outroDia = dto(DIA.plusDays(1), null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, outroDia))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Desvincule");
    }

    @Test
    void osComTransfersNaoMudaDeDiaNoPatch() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(osComTransfer()));
        OrdemServicoRequestDTO outroDia = dto(DIA.plusDays(1), null, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, outroDia))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("1 transfer(s)");
    }

    @Test
    void osComTransfersPodeSerEditadaMantendoODia() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(osComTransfer()));

        // When
        OrdemServicoResponseDTO salva = service.atualizar(1L, dto(DIA, null, null, StatusOrdemServico.EM_ANDAMENTO, 1L));

        // Then
        assertThat(salva.status()).isEqualTo(StatusOrdemServico.EM_ANDAMENTO);
        assertThat(salva.dataServico()).isEqualTo(DIA);
    }

    @Test
    void putSemStatusVoltaPraAbertaEAuditaAMudanca() {
        // Given
        OrdemServico emAndamento = os(1L, null);
        emAndamento.setStatus(StatusOrdemServico.EM_ANDAMENTO);
        when(repository.findById(1L)).thenReturn(Optional.of(emAndamento));

        // When
        OrdemServicoResponseDTO salva = service.atualizar(1L, dto(DIA, null, null, null, null));

        // Then
        assertThat(salva.status()).isEqualTo(StatusOrdemServico.ABERTA);
        verify(auditoriaService).registrarAtualizacao(eq("ordens_servico"), eq(1L), any(),
                eq(StatusOrdemServico.EM_ANDAMENTO), eq(StatusOrdemServico.ABERTA));
    }

    @Test
    void patchComTodosOsCamposTrocaTudo() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).build()));
        when(veiculoRepository.findById(6L)).thenReturn(Optional.of(Veiculo.builder().id(6L).build()));

        // When
        OrdemServicoResponseDTO salva = service.atualizarParcial(1L, dto(DIA.plusDays(2), 5L, 6L, StatusOrdemServico.FINALIZADA, null));

        // Then
        assertThat(salva.dataServico()).isEqualTo(DIA.plusDays(2));
        assertThat(salva.motoristaId()).isEqualTo(5L);
        assertThat(salva.veiculoId()).isEqualTo(6L);
        assertThat(salva.status()).isEqualTo(StatusOrdemServico.FINALIZADA);
    }

    @Test
    void patchVazioNaoMudaNada() {
        // Given
        OrdemServico finalizada = os(1L, 5L);
        finalizada.setStatus(StatusOrdemServico.FINALIZADA);
        when(repository.findById(1L)).thenReturn(Optional.of(finalizada));

        // When
        OrdemServicoResponseDTO salva = service.atualizarParcial(1L, dto(null, null, null, null, null));

        // Then
        assertThat(salva.status()).isEqualTo(StatusOrdemServico.FINALIZADA);
        assertThat(salva.dataServico()).isEqualTo(DIA);
        assertThat(salva.motoristaId()).isEqualTo(5L);
    }

    @Test
    void excluirOsExistenteApagaEAudita() {
        // Given
        when(repository.existsById(1L)).thenReturn(true);

        // When
        service.deletar(1L);

        // Then
        verify(repository).deleteById(1L);
        verify(auditoriaService).registrar("ordens_servico", 1L, "Ordem de serviço removida");
    }

    @Test
    void excluirOsInexistenteLanca404SemApagar() {
        // Given
        when(repository.existsById(9L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        verify(repository, never()).deleteById(anyLong());
        verifyNoInteractions(auditoriaService);
    }
}
