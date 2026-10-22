package com.AJTBackend.service;

import com.AJTBackend.dto.FiltroOrdemServicoDTO;
import com.AJTBackend.dto.OrdemServicoRequestDTO;
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
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private static OrdemServicoRequestDTO dto(LocalDate data, Long motoristaId, Long veiculoId, StatusOrdemServico status, Long versao) {
        return new OrdemServicoRequestDTO(data, motoristaId, veiculoId, status, versao);
    }

    private static FiltroOrdemServicoDTO filtro(LocalDate inicio, LocalDate fim) {
        return new FiltroOrdemServicoDTO(null, inicio, fim, null, null);
    }

    // ---------------- leitura ----------------

    @Test
    void periodoInvertidoLanca400SemConsultar() {
        assertThatThrownBy(() -> service.listar(filtro(DIA, DIA.minusDays(1)), PageRequest.of(0, 5)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("depois da final");
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listaPraGerenteEPraMotoristaComESemVinculo() {
        var pagina = PageRequest.of(0, 5);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(os(1L, 5L)), pagina, 1));

        when(acessoMotorista.restrito()).thenReturn(false);
        assertThat(service.listar(new FiltroOrdemServicoDTO(StatusOrdemServico.ABERTA, DIA, DIA, 5L, 2L), pagina).totalElementos()).isEqualTo(1);

        when(acessoMotorista.restrito()).thenReturn(true);
        when(acessoMotorista.motoristaId()).thenReturn(Optional.of(5L));
        assertThat(service.buscarPorStatus(StatusOrdemServico.ABERTA, pagina).conteudo()).hasSize(1);

        when(acessoMotorista.motoristaId()).thenReturn(Optional.empty());
        assertThat(service.listar(filtro(null, null), pagina).conteudo()).hasSize(1); // o repositorio mockado ignora o filtro "nenhum"
    }

    @Test
    void motoristaNaoVeOsDeOutroE404PraQuemNaoExiste() {
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, 6L)));
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(acessoMotorista.podeVer(any())).thenReturn(false);

        assertThatThrownBy(() -> service.buscarPorId(1L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void buscaPorIdVisivelDevolveAOs() {
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, 5L)));
        when(acessoMotorista.podeVer(any())).thenReturn(true);

        assertThat(service.buscarPorId(1L).motoristaId()).isEqualTo(5L);
    }

    @Test
    void minhasDoDiaSemVinculoEVaziaEComVinculoTrazAsParadas() {
        when(acessoMotorista.motoristaId()).thenReturn(Optional.empty());
        assertThat(service.minhasDoDia(DIA)).isEmpty();
        verify(repository, never()).findAll(any(Specification.class), any(Sort.class));

        when(acessoMotorista.motoristaId()).thenReturn(Optional.of(5L));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(os(1L, 5L)));
        when(paradaOsService.listarPorOrdemServico(1L)).thenReturn(List.of(
                new ParadaOsResponseDTO(3L, 1L, 1, "Aeroporto", null, null, null, null, null, java.util.Set.of(), 0L)));

        var minhas = service.minhasDoDia(null); // sem data = hoje
        assertThat(minhas).hasSize(1);
        assertThat(minhas.get(0).paradas()).extracting(ParadaOsResponseDTO::localParada).containsExactly("Aeroporto");
    }

    // ---------------- escrita ----------------

    @Test
    void cadastroComMotoristaOuVeiculoInexistenteLanca404() {
        when(motoristaRepository.findById(5L)).thenReturn(Optional.empty());
        when(veiculoRepository.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(dto(DIA, 5L, null, null, null))).isInstanceOf(MotoristaNaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(dto(DIA, null, 6L, null, null))).isInstanceOf(VeiculoNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroSemStatusNasceAbertaEAudita() {
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).build()));
        when(veiculoRepository.findById(6L)).thenReturn(Optional.of(Veiculo.builder().id(6L).build()));
        when(repository.save(any())).thenAnswer(inv -> {
            OrdemServico salva = inv.getArgument(0);
            salva.setId(10L);
            return salva;
        });

        var criada = service.criar(dto(DIA, 5L, 6L, null, null));

        assertThat(criada.status()).isEqualTo(StatusOrdemServico.ABERTA);
        assertThat(criada.veiculoId()).isEqualTo(6L);
        verify(auditoriaService).registrar("ordens_servico", 10L, "Ordem de servico criada");
    }

    @Test
    void edicaoDeOsInexistenteLanca404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(9L, dto(DIA, null, null, null, null))).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(9L, dto(null, null, null, null, null))).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void versaoVelhaLanca409() {
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));

        assertThatThrownBy(() -> service.atualizar(1L, dto(DIA, null, null, null, 0L))).isInstanceOf(ConflitoDeVersaoException.class);
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto(null, null, null, null, 0L))).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void osSemTransfersMudaDeDiaEComTransfersPodeSerEditadaNoMesmoDia() {
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));
        assertThat(service.atualizar(1L, dto(DIA.plusDays(3), null, null, null, null)).dataServico())
                .isEqualTo(DIA.plusDays(3));

        OrdemServico comTransfer = os(2L, null);
        comTransfer.setTransfers(new ArrayList<>(List.of(Transfer.builder().id(2L).build())));
        when(repository.findById(2L)).thenReturn(Optional.of(comTransfer));
        assertThat(service.atualizar(2L, dto(DIA, null, null, StatusOrdemServico.EM_ANDAMENTO, null)).status())
                .isEqualTo(StatusOrdemServico.EM_ANDAMENTO);
    }

    @Test
    void osComTransfersNaoMudaDeDiaNoPutNemNoPatch() {
        OrdemServico comTransfer = os(1L, null);
        comTransfer.setTransfers(new ArrayList<>(List.of(Transfer.builder().id(2L).build())));
        when(repository.findById(1L)).thenReturn(Optional.of(comTransfer));

        assertThatThrownBy(() -> service.atualizar(1L, dto(DIA.plusDays(1), null, null, null, null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Desvincule");
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto(DIA.plusDays(1), null, null, null, null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("1 transfer(s)");

        // mesma data: pode salvar
        assertThat(service.atualizar(1L, dto(DIA, null, null, StatusOrdemServico.EM_ANDAMENTO, 1L)).status())
                .isEqualTo(StatusOrdemServico.EM_ANDAMENTO);
    }

    @Test
    void putSemStatusVoltaPraAbertaEAuditaAMudanca() {
        OrdemServico emAndamento = os(1L, null);
        emAndamento.setStatus(StatusOrdemServico.EM_ANDAMENTO);
        when(repository.findById(1L)).thenReturn(Optional.of(emAndamento));

        assertThat(service.atualizar(1L, dto(DIA, null, null, null, null)).status()).isEqualTo(StatusOrdemServico.ABERTA);
        verify(auditoriaService).registrarAtualizacao(eq("ordens_servico"), eq(1L), any(),
                eq(StatusOrdemServico.EM_ANDAMENTO), eq(StatusOrdemServico.ABERTA));
    }

    @Test
    void patchMudaSoOQueVeio() {
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).build()));
        when(veiculoRepository.findById(6L)).thenReturn(Optional.of(Veiculo.builder().id(6L).build()));

        var tudo = service.atualizarParcial(1L, dto(DIA.plusDays(2), 5L, 6L, StatusOrdemServico.FINALIZADA, null));
        assertThat(tudo.dataServico()).isEqualTo(DIA.plusDays(2));
        assertThat(tudo.motoristaId()).isEqualTo(5L);
        assertThat(tudo.veiculoId()).isEqualTo(6L);
        assertThat(tudo.status()).isEqualTo(StatusOrdemServico.FINALIZADA);

        var nada = service.atualizarParcial(1L, dto(null, null, null, null, null));
        assertThat(nada.status()).isEqualTo(StatusOrdemServico.FINALIZADA);
    }

    @Test
    void excluirInexistenteLanca404EExistenteApagaEAudita() {
        when(repository.existsById(9L)).thenReturn(false);
        when(repository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        verify(repository, never()).deleteById(anyLong());

        service.deletar(1L);
        verify(repository).deleteById(1L);
        verify(auditoriaService).registrar("ordens_servico", 1L, "Ordem de servico removida");
    }
}
