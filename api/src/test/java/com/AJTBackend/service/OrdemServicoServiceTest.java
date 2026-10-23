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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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
    private static final PageRequest PAGINA = PageRequest.of(0, 5);

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

    private static OrdemServico osComUmTransfer(Long id) {
        OrdemServico comTransfer = os(id, null);
        comTransfer.setTransfers(new ArrayList<>(List.of(Transfer.builder().id(2L).build())));
        return comTransfer;
    }

    private static OrdemServicoRequestDTO dto(LocalDate data, Long motoristaId, Long veiculoId, StatusOrdemServico status, Long versao) {
        return new OrdemServicoRequestDTO(data, motoristaId, veiculoId, status, versao);
    }

    private static FiltroOrdemServicoDTO filtro(LocalDate inicio, LocalDate fim) {
        return new FiltroOrdemServicoDTO(null, inicio, fim, null, null);
    }

    private void repositorioDevolveUmaOsDoMotorista5() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(os(1L, 5L)), PAGINA, 1));
    }

    private void motorista5EVeiculo6Cadastrados() {
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).build()));
        when(veiculoRepository.findById(6L)).thenReturn(Optional.of(Veiculo.builder().id(6L).build()));
    }

    // ---------------- leitura ----------------

    @Test
    void deveLancarRegraNegocioSemConsultar_quandoPeriodoEstaInvertido() {
        // when
        Throwable erro = catchThrowable(() -> service.listar(filtro(DIA, DIA.minusDays(1)), PAGINA));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("depois da final");
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void deveListarComTodosOsFiltros_quandoUsuarioNaoEhRestrito() {
        // given
        repositorioDevolveUmaOsDoMotorista5();
        when(acessoMotorista.restrito()).thenReturn(false);

        // when
        PaginaResponseDTO<OrdemServicoResponseDTO> pagina =
                service.listar(new FiltroOrdemServicoDTO(StatusOrdemServico.ABERTA, DIA, DIA, 5L, 2L), PAGINA);

        // then
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    void deveListarPorStatus_quandoMotoristaTemVinculo() {
        // given
        repositorioDevolveUmaOsDoMotorista5();
        when(acessoMotorista.restrito()).thenReturn(true);
        when(acessoMotorista.motoristaId()).thenReturn(Optional.of(5L));

        // when
        PaginaResponseDTO<OrdemServicoResponseDTO> pagina = service.buscarPorStatus(StatusOrdemServico.ABERTA, PAGINA);

        // then
        assertThat(pagina.conteudo()).hasSize(1);
    }

    @Test
    void deveConsultarComFiltroNenhum_quandoMotoristaNaoTemVinculo() {
        // given
        repositorioDevolveUmaOsDoMotorista5();
        when(acessoMotorista.restrito()).thenReturn(true);
        when(acessoMotorista.motoristaId()).thenReturn(Optional.empty());

        // when
        PaginaResponseDTO<OrdemServicoResponseDTO> pagina = service.listar(filtro(null, null), PAGINA);

        // then
        // o repositorio mockado ignora o filtro "nenhum"; o resultado real fica no FiltrosNoBancoRepositoryTest
        assertThat(pagina.conteudo()).hasSize(1);
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void deveLancarOsNaoEncontrada_quandoMotoristaBuscaOsDeOutro() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, 6L)));
        when(acessoMotorista.podeVer(any())).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(1L));

        // then
        assertThat(erro).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void deveLancarOsNaoEncontrada_quandoOsNaoExiste() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void deveDevolverAOs_quandoUsuarioPodeVer() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, 5L)));
        when(acessoMotorista.podeVer(any())).thenReturn(true);

        // when
        OrdemServicoResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.motoristaId()).isEqualTo(5L);
    }

    @Test
    void deveDevolverListaVaziaSemConsultar_quandoMotoristaSemVinculoPedeAsDoDia() {
        // given
        when(acessoMotorista.motoristaId()).thenReturn(Optional.empty());

        // when
        List<OrdemServicoDoDiaDTO> minhas = service.minhasDoDia(DIA);

        // then
        assertThat(minhas).isEmpty();
        verify(repository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void deveTrazerAsOsComParadas_quandoMotoristaComVinculoPedeAsDeHoje() {
        // given
        when(acessoMotorista.motoristaId()).thenReturn(Optional.of(5L));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(os(1L, 5L)));
        when(paradaOsService.listarPorOrdemServico(1L)).thenReturn(List.of(
                new ParadaOsResponseDTO(3L, 1L, 1, "Aeroporto", null, null, null, null, null, Set.of(), 0L)));

        // when
        List<OrdemServicoDoDiaDTO> minhas = service.minhasDoDia(null); // sem data = hoje

        // then
        assertThat(minhas).hasSize(1);
        assertThat(minhas.get(0).paradas()).extracting(ParadaOsResponseDTO::localParada).containsExactly("Aeroporto");
    }

    // ---------------- escrita ----------------

    @Test
    void deveLancarMotoristaNaoEncontradoSemGravar_quandoCadastroTemMotoristaInexistente() {
        // given
        when(motoristaRepository.findById(5L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto(DIA, 5L, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(MotoristaNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveLancarVeiculoNaoEncontradoSemGravar_quandoCadastroTemVeiculoInexistente() {
        // given
        when(veiculoRepository.findById(6L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto(DIA, null, 6L, null, null)));

        // then
        assertThat(erro).isInstanceOf(VeiculoNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveNascerAbertaEAuditar_quandoCadastroNaoTrazStatus() {
        // given
        motorista5EVeiculo6Cadastrados();
        when(repository.save(any())).thenAnswer(inv -> {
            OrdemServico salva = inv.getArgument(0);
            salva.setId(10L);
            return salva;
        });

        // when
        OrdemServicoResponseDTO criada = service.criar(dto(DIA, 5L, 6L, null, null));

        // then
        assertThat(criada.status()).isEqualTo(StatusOrdemServico.ABERTA);
        assertThat(criada.veiculoId()).isEqualTo(6L);
        verify(auditoriaService).registrar("ordens_servico", 10L, "Ordem de servico criada");
    }

    @Test
    void deveLancarOsNaoEncontrada_quandoPutEmOsInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, dto(DIA, null, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void deveLancarOsNaoEncontrada_quandoPatchEmOsInexistente() {
        // given
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(9L, dto(null, null, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void deveLancarConflitoDeVersao_quandoPutTrazVersaoVelha() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto(DIA, null, null, null, 0L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveLancarConflitoDeVersao_quandoPatchTrazVersaoVelha() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, dto(null, null, null, null, 0L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveMudarODia_quandoOsNaoTemTransfers() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));

        // when
        OrdemServicoResponseDTO resposta = service.atualizar(1L, dto(DIA.plusDays(3), null, null, null, null));

        // then
        assertThat(resposta.dataServico()).isEqualTo(DIA.plusDays(3));
    }

    @Test
    void devePermitirEditar_quandoOsComTransfersMantemODiaSemVersao() {
        // given
        when(repository.findById(2L)).thenReturn(Optional.of(osComUmTransfer(2L)));

        // when
        OrdemServicoResponseDTO resposta = service.atualizar(2L, dto(DIA, null, null, StatusOrdemServico.EM_ANDAMENTO, null));

        // then
        assertThat(resposta.status()).isEqualTo(StatusOrdemServico.EM_ANDAMENTO);
    }

    @Test
    void devePermitirEditar_quandoOsComTransfersMantemODiaComVersaoAtual() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(osComUmTransfer(1L)));

        // when
        OrdemServicoResponseDTO resposta = service.atualizar(1L, dto(DIA, null, null, StatusOrdemServico.EM_ANDAMENTO, 1L));

        // then
        assertThat(resposta.status()).isEqualTo(StatusOrdemServico.EM_ANDAMENTO);
    }

    @Test
    void deveLancarRegraNegocio_quandoPutMudaODiaDeOsComTransfers() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(osComUmTransfer(1L)));

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, dto(DIA.plusDays(1), null, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(RegraNegocioException.class).hasMessageContaining("Desvincule");
    }

    @Test
    void deveLancarRegraNegocio_quandoPatchMudaODiaDeOsComTransfers() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(osComUmTransfer(1L)));

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, dto(DIA.plusDays(1), null, null, null, null)));

        // then
        assertThat(erro).isInstanceOf(RegraNegocioException.class).hasMessageContaining("1 transfer(s)");
    }

    @Test
    void deveVoltarPraAbertaEAuditarAMudanca_quandoPutNaoTrazStatus() {
        // given
        OrdemServico emAndamento = os(1L, null);
        emAndamento.setStatus(StatusOrdemServico.EM_ANDAMENTO);
        when(repository.findById(1L)).thenReturn(Optional.of(emAndamento));

        // when
        OrdemServicoResponseDTO resposta = service.atualizar(1L, dto(DIA, null, null, null, null));

        // then
        assertThat(resposta.status()).isEqualTo(StatusOrdemServico.ABERTA);
        verify(auditoriaService).registrarAtualizacao(eq("ordens_servico"), eq(1L), any(),
                eq(StatusOrdemServico.EM_ANDAMENTO), eq(StatusOrdemServico.ABERTA));
    }

    @Test
    void deveAlterarTodosOsCampos_quandoPatchTrazTodos() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(os(1L, null)));
        motorista5EVeiculo6Cadastrados();

        // when
        OrdemServicoResponseDTO tudo = service.atualizarParcial(1L, dto(DIA.plusDays(2), 5L, 6L, StatusOrdemServico.FINALIZADA, null));

        // then
        assertThat(tudo.dataServico()).isEqualTo(DIA.plusDays(2));
        assertThat(tudo.motoristaId()).isEqualTo(5L);
        assertThat(tudo.veiculoId()).isEqualTo(6L);
        assertThat(tudo.status()).isEqualTo(StatusOrdemServico.FINALIZADA);
    }

    @Test
    void naoDeveMudarNada_quandoPatchVemVazio() {
        // given
        OrdemServico finalizada = os(1L, null);
        finalizada.setStatus(StatusOrdemServico.FINALIZADA);
        when(repository.findById(1L)).thenReturn(Optional.of(finalizada));

        // when
        OrdemServicoResponseDTO nada = service.atualizarParcial(1L, dto(null, null, null, null, null));

        // then
        assertThat(nada.status()).isEqualTo(StatusOrdemServico.FINALIZADA);
        assertThat(nada.dataServico()).isEqualTo(DIA);
    }

    @Test
    void deveLancarOsNaoEncontradaSemApagar_quandoExcluiOsInexistente() {
        // given
        when(repository.existsById(9L)).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void deveApagarEAuditar_quandoExcluiOsExistente() {
        // given
        when(repository.existsById(1L)).thenReturn(true);

        // when
        service.deletar(1L);

        // then
        verify(repository).deleteById(1L);
        verify(auditoriaService).registrar("ordens_servico", 1L, "Ordem de servico removida");
    }
}
