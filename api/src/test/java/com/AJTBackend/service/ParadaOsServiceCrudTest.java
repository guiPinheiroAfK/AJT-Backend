package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.ParadaOsRequestDTO;
import com.AJTBackend.dto.ParadaOsResponseDTO;
import com.AJTBackend.exception.ConflitoDeVersaoException;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.ParadaOsNaoEncontradaException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.AcaoParada;
import com.AJTBackend.model.enums.StatusParada;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.AJTBackend.repository.TransferRepository;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: o restante das regras do ParadaOsService (o ParadaOsServiceTest cobre o PATCH
 * do motorista) — cadastro e PUT com OS inexistente (404), status padrao PENDENTE, versao
 * velha (409), PATCH campo a campo, exclusao de quem nao existe, a listagem geral pro
 * motorista (so das OS dele; sem vinculo, vazia) e o motorista tentando mexer em cada um
 * dos outros campos (403).
 *
 * como rodar: teste unitario com repositorios mockados; o perfil logado e simulado no
 * SecurityContextHolder. sem spring, sem banco. "mvn test".
 *
 * por que existe: parada errada e o motorista no lugar errado na hora errada.
 */
class ParadaOsServiceCrudTest {

    private static final PageRequest PAGINA = PageRequest.of(0, 5);

    private final ParadaOsRepository repository = mock(ParadaOsRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ParadaOsService service = new ParadaOsService(repository, ordemServicoRepository, transferRepository,
            new UsuarioLogado(), new AcessoMotorista(new UsuarioLogado(), usuarioRepository));

    private static final OrdemServico OS = OrdemServico.builder().id(3L).dataServico(LocalDate.of(2026, 10, 21))
            .motorista(Motorista.builder().id(5L).build()).build();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComo(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void motoristaLigadoAoCadastro5() {
        logadoComo("MOTORISTA");
        when(usuarioRepository.findByUsername("usuario")).thenReturn(Optional.of(Usuario.builder()
                .username("usuario").motorista(Motorista.builder().id(5L).build()).build()));
    }

    private void gerenteComParadaEOsCadastradas() {
        logadoComo("GERENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
    }

    private static ParadaOs parada() {
        return ParadaOs.builder().id(1L).versao(4L).ordemServico(OS).ordemParada(1).localParada("Portao 3").build();
    }

    private static ParadaOsRequestDTO completo(Long osId, Long versao) {
        return new ParadaOsRequestDTO(osId, 2, "Hotel", -25.0, -54.0, LocalTime.NOON, AcaoParada.DESEMBARQUE, null, Set.of(), versao);
    }

    private static ParadaOsRequestDTO comStatus(StatusParada status, Long versao) {
        return new ParadaOsRequestDTO(3L, 1, "Portao 3", null, null, null, null, status, Set.of(), versao);
    }

    // ----- cadastro -----

    @Test
    void deveRespeitarOStatus_quandoCadastroInformaStatus() {
        // given
        logadoComo("GERENTE");
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        ParadaOsResponseDTO criada = service.criar(comStatus(StatusParada.EM_ANDAMENTO, null));

        // then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.EM_ANDAMENTO);
    }

    @Test
    void deveLancarOsNaoEncontradaSemGravar_quandoCadastroApontaOsInexistente() {
        // given
        logadoComo("GERENTE");
        when(ordemServicoRepository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.criar(completo(9L, null)));

        // then
        assertThat(erro).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveNascerPendente_quandoCadastroNaoTrazStatus() {
        // given
        logadoComo("GERENTE");
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        ParadaOsResponseDTO criada = service.criar(completo(3L, null));

        // then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.PENDENTE);
        assertThat(criada.transferIds()).isEmpty();
        assertThat(criada.acao()).isEqualTo(AcaoParada.DESEMBARQUE);
    }

    // ----- leitura -----

    @Test
    void deveLancarParadaNaoEncontrada_quandoBuscaIdInexistente() {
        // given
        logadoComo("GERENTE");
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro).isInstanceOf(ParadaOsNaoEncontradaException.class);
    }

    @Test
    void deveDevolverPaginaVaziaSemConsultar_quandoMotoristaSemVinculoListaTudo() {
        // given
        logadoComo("MOTORISTA");

        // when
        PaginaResponseDTO<ParadaOsResponseDTO> pagina = service.listarTodos(PAGINA);

        // then
        assertThat(pagina.totalElementos()).isZero();
        verify(repository, never()).findAll(PAGINA);
    }

    @Test
    void deveTrazerSoAsParadasDasOsDele_quandoMotoristaComVinculoListaTudo() {
        // given
        motoristaLigadoAoCadastro5();
        when(repository.findByOrdemServicoMotoristaId(5L, PAGINA)).thenReturn(new PageImpl<>(List.of(parada()), PAGINA, 1));

        // when
        PaginaResponseDTO<ParadaOsResponseDTO> pagina = service.listarTodos(PAGINA);

        // then
        assertThat(pagina.conteudo()).hasSize(1);
        verify(repository, never()).findAll(PAGINA);
    }

    @Test
    void deveTrazerTodasAsParadas_quandoGerenteListaTudo() {
        // given
        logadoComo("GERENTE");
        when(repository.findAll(PAGINA)).thenReturn(new PageImpl<>(List.of(parada(), parada()), PAGINA, 2));

        // when
        PaginaResponseDTO<ParadaOsResponseDTO> pagina = service.listarTodos(PAGINA);

        // then
        assertThat(pagina.totalElementos()).isEqualTo(2);
    }

    @Test
    void deveListarAsParadas_quandoMotoristaPodeVerAOs() {
        // given
        motoristaLigadoAoCadastro5();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
        when(repository.findByOrdemServicoIdOrderByOrdemParadaAsc(3L)).thenReturn(List.of(parada()));

        // when
        List<ParadaOsResponseDTO> paradas = service.listarPorOrdemServico(3L);

        // then
        assertThat(paradas).hasSize(1);
    }

    @Test
    void deveDevolverAParada_quandoMotoristaPodeVerAOsDela() {
        // given
        motoristaLigadoAoCadastro5();
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));

        // when
        ParadaOsResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.localParada()).isEqualTo("Portao 3");
    }

    // ----- PUT -----

    @Test
    void deveRespeitarOStatus_quandoPutInformaStatus() {
        // given
        gerenteComParadaEOsCadastradas();

        // when
        ParadaOsResponseDTO editada = service.atualizar(1L, comStatus(StatusParada.CONCLUIDA, 4L));

        // then
        assertThat(editada.statusParada()).isEqualTo(StatusParada.CONCLUIDA);
    }

    @Test
    void deveLancarParadaNaoEncontrada_quandoPutEmParadaInexistente() {
        // given
        logadoComo("GERENTE");
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, completo(3L, null)));

        // then
        assertThat(erro).isInstanceOf(ParadaOsNaoEncontradaException.class);
    }

    @Test
    void deveLancarConflitoDeVersao_quandoPutTrazVersaoVelha() {
        // given
        gerenteComParadaEOsCadastradas();

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, completo(3L, 1L)));

        // then
        assertThat(erro).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void deveLancarOsNaoEncontrada_quandoPutApontaOsInexistente() {
        // given
        gerenteComParadaEOsCadastradas();
        when(ordemServicoRepository.findById(8L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(1L, completo(8L, null)));

        // then
        assertThat(erro).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void deveVoltarPraPendente_quandoPutValidoNaoTrazStatus() {
        // given
        gerenteComParadaEOsCadastradas();

        // when
        ParadaOsResponseDTO salva = service.atualizar(1L, completo(3L, 4L));

        // then
        assertThat(salva.localParada()).isEqualTo("Hotel");
        assertThat(salva.statusParada()).isEqualTo(StatusParada.PENDENTE);
    }

    // ----- PATCH e exclusao -----

    @Test
    void deveAlterarCadaCampo_quandoGerenteFazPatchCompleto() {
        // given
        gerenteComParadaEOsCadastradas();
        when(transferRepository.findAllById(any())).thenReturn(List.of(Transfer.builder().id(7L).build()));

        // when
        ParadaOsResponseDTO tudo = service.atualizarParcial(1L, new ParadaOsRequestDTO(3L, 5, "Hotel B", 1.0, 2.0, LocalTime.MIDNIGHT,
                AcaoParada.EMBARQUE, StatusParada.EM_ANDAMENTO, Set.of(7L), null));

        // then
        assertThat(tudo.ordemParada()).isEqualTo(5);
        assertThat(tudo.localParada()).isEqualTo("Hotel B");
        assertThat(tudo.latitude()).isEqualTo(1.0);
        assertThat(tudo.longitude()).isEqualTo(2.0);
        assertThat(tudo.horarioPrevisto()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(tudo.acao()).isEqualTo(AcaoParada.EMBARQUE);
        assertThat(tudo.statusParada()).isEqualTo(StatusParada.EM_ANDAMENTO);
        assertThat(tudo.transferIds()).containsExactly(7L);
    }

    @Test
    void deveLancarParadaNaoEncontradaSemApagar_quandoExcluiIdInexistente() {
        // given
        logadoComo("GERENTE");
        when(repository.existsById(9L)).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro).isInstanceOf(ParadaOsNaoEncontradaException.class);
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void deveApagar_quandoExcluiIdExistente() {
        // given
        logadoComo("GERENTE");
        when(repository.existsById(1L)).thenReturn(true);

        // when
        service.deletar(1L);

        // then
        verify(repository).deleteById(1L);
    }

    static Stream<Arguments> camposProibidosProMotorista() {
        return Stream.of(
                Arguments.of("os", new ParadaOsRequestDTO(3L, null, null, null, null, null, null, null, null, null)),
                Arguments.of("ordem", new ParadaOsRequestDTO(null, 2, null, null, null, null, null, null, null, null)),
                Arguments.of("latitude", new ParadaOsRequestDTO(null, null, null, 1.0, null, null, null, null, null, null)),
                Arguments.of("longitude", new ParadaOsRequestDTO(null, null, null, null, 1.0, null, null, null, null, null)),
                Arguments.of("horario", new ParadaOsRequestDTO(null, null, null, null, null, LocalTime.NOON, null, null, null, null)),
                Arguments.of("acao", new ParadaOsRequestDTO(null, null, null, null, null, null, AcaoParada.EMBARQUE, null, null, null)),
                Arguments.of("transfers", new ParadaOsRequestDTO(null, null, null, null, null, null, null, null, Set.of(1L), null)));
    }

    @ParameterizedTest(name = "motorista mexendo em {0} -> 403")
    @MethodSource("camposProibidosProMotorista")
    void deveNegarSemConsultar_quandoMotoristaMexeEmCampoQueNaoEhStatus(String campo, ParadaOsRequestDTO dto) {
        // given
        logadoComo("MOTORISTA");

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, dto));

        // then
        assertThat(erro).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findById(anyLong());
    }
}
