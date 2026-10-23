package com.ajtbackend.service;

import com.ajtbackend.config.UsuarioLogado;
import com.ajtbackend.dto.PaginaResponseDTO;
import com.ajtbackend.dto.ParadaOsRequestDTO;
import com.ajtbackend.dto.ParadaOsResponseDTO;
import com.ajtbackend.exception.OrdemServicoNaoEncontradoException;
import com.ajtbackend.exception.ParadaOsNaoEncontradaException;
import com.ajtbackend.exception.TransferNaoEncontradoException;
import com.ajtbackend.model.Motorista;
import com.ajtbackend.model.OrdemServico;
import com.ajtbackend.model.ParadaOs;
import com.ajtbackend.model.Transfer;
import com.ajtbackend.model.Usuario;
import com.ajtbackend.model.enums.StatusParada;
import com.ajtbackend.repository.OrdemServicoRepository;
import com.ajtbackend.repository.ParadaOsRepository;
import com.ajtbackend.repository.TransferRepository;
import com.ajtbackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
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
 * o que testa: o ParadaOsService do ponto de vista de cada perfil: o motorista so muda o
 * status e so das paradas das proprias OS (404 nas de outro, HU07), o gerente mexe em
 * qualquer campo, a troca de transfers numa consulta so, as listagens e os 404 de parada
 * ou OS inexistente.
 *
 * como rodar: teste unitario com repositorios mockados; o perfil logado e simulado no
 * SecurityContextHolder. sem spring, sem banco. "mvn test".
 *
 * por que existe: o motorista usa o celular na rua para marcar a parada como concluida.
 * se ele conseguir mudar local ou ordem, ou enxergar a parada de outro motorista, a rota
 * do dia vira bagunca. o restante do CRUD fica no ParadaOsServiceCrudTest.
 */
class ParadaOsServiceTest {

    private final ParadaOsRepository paradaOsRepository = mock(ParadaOsRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ParadaOsService service = new ParadaOsService(paradaOsRepository, ordemServicoRepository, transferRepository,
            new UsuarioLogado(), new AcessoMotorista(new UsuarioLogado(), usuarioRepository));

    private static final Pageable PAGINA = PageRequest.of(0, 5);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    // o usuario logado ("usuario") e o cadastro de motorista 5, dono da OS da parada()
    private void usuarioLigadoAoMotorista(long motoristaId) {
        when(usuarioRepository.findByUsername("usuario")).thenReturn(Optional.of(Usuario.builder()
                .id(9L).username("usuario").motorista(Motorista.builder().id(motoristaId).build()).build()));
    }

    private static void logadoComo(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static OrdemServico ordemServico() {
        return OrdemServico.builder().id(3L).dataServico(LocalDate.now())
                .motorista(Motorista.builder().id(5L).build()).build();
    }

    private static ParadaOs parada() {
        return ParadaOs.builder().id(1L).ordemServico(ordemServico()).ordemParada(1).localParada("Portao 3").build();
    }

    private static ParadaOsRequestDTO patch(String local, StatusParada status, Set<Long> transferIds) {
        return new ParadaOsRequestDTO(null, null, local, null, null, null, null, status, transferIds, null);
    }

    private static ParadaOsRequestDTO corpo(Long osId, String local, StatusParada status) {
        return new ParadaOsRequestDTO(osId, 1, local, null, null, null, null, status, null, null);
    }

    // campos que o motorista nao pode alterar (alem do status)
    static Stream<ParadaOsRequestDTO> patchesQueMexemEmOutrosCampos() {
        return Stream.of(
                patch("Outro local", StatusParada.CONCLUIDA, null),
                patch(null, StatusParada.CONCLUIDA, Set.of(7L)),
                new ParadaOsRequestDTO(4L, null, null, null, null, null, null, StatusParada.CONCLUIDA, null, null),
                new ParadaOsRequestDTO(null, 2, null, null, null, null, null, StatusParada.CONCLUIDA, null, null));
    }

    // ----- perfil MOTORISTA -----

    @Test
    void deveAtualizarSoOStatus_quandoMotoristaDonoDaOsFazPatchDeStatus() {
        // given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(5L);
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        // when
        ParadaOsResponseDTO resposta = service.atualizarParcial(1L, patch(null, StatusParada.CONCLUIDA, null));

        // then
        assertThat(resposta.statusParada()).isEqualTo(StatusParada.CONCLUIDA);
        assertThat(parada.getStatusParada()).isEqualTo(StatusParada.CONCLUIDA);
        assertThat(parada.getLocalParada()).isEqualTo("Portao 3");
        verify(paradaOsRepository).flush();
    }

    @ParameterizedTest(name = "[{index}] motorista mexendo em outro campo -> 403")
    @MethodSource("patchesQueMexemEmOutrosCampos")
    void deveNegarSemConsultar_quandoMotoristaMexeEmOutroCampoJuntoComOStatus(ParadaOsRequestDTO patch) {
        // given
        logadoComo("MOTORISTA");

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("só pode alterar o status");
        verify(paradaOsRepository, never()).findById(anyLong());
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void deveLancarParadaNaoEncontrada_quandoMotoristaMexeEmParadaDeOutro() {
        // given
        // 404 e nao 403, o motorista nem fica sabendo que a parada existe (HU07)
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(6L);
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch(null, StatusParada.CONCLUIDA, null)));

        // then
        assertThat(erro)
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("1");
        assertThat(parada.getStatusParada()).isNotEqualTo(StatusParada.CONCLUIDA);
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void deveLancarParadaNaoEncontrada_quandoMotoristaSemCadastroLigadoBuscaParada() {
        // given
        logadoComo("MOTORISTA");
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada()));

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(1L));

        // then
        assertThat(erro)
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("1");
    }

    @Test
    void deveDevolverAParada_quandoMotoristaBuscaParadaDaPropriaOs() {
        // given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(5L);
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada()));

        // when
        ParadaOsResponseDTO resposta = service.buscarPorId(1L);

        // then
        assertThat(resposta.id()).isEqualTo(1L);
        assertThat(resposta.osId()).isEqualTo(3L);
        assertThat(resposta.localParada()).isEqualTo("Portao 3");
    }

    // ----- listagens -----

    @Test
    void deveListarSoAsParadasDasPropriasOs_quandoMotoristaComVinculoListaTudo() {
        // given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(5L);
        when(paradaOsRepository.findByOrdemServicoMotoristaId(5L, PAGINA))
                .thenReturn(new PageImpl<>(List.of(parada()), PAGINA, 1));

        // when
        PaginaResponseDTO<ParadaOsResponseDTO> resultado = service.listarTodos(PAGINA);

        // then
        assertThat(resultado.conteudo()).extracting(ParadaOsResponseDTO::id).containsExactly(1L);
        verify(paradaOsRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void deveDevolverListaVaziaSemConsultar_quandoMotoristaSemCadastroLigadoListaTudo() {
        // given
        logadoComo("MOTORISTA");

        // when
        PaginaResponseDTO<ParadaOsResponseDTO> resultado = service.listarTodos(PAGINA);

        // then
        assertThat(resultado.conteudo()).isEmpty();
        verify(paradaOsRepository, never()).findAll(any(Pageable.class));
        verify(paradaOsRepository, never()).findByOrdemServicoMotoristaId(anyLong(), any(Pageable.class));
    }

    @Test
    void deveListarTodasAsParadas_quandoGerenteListaTudo() {
        // given
        logadoComo("GERENTE");
        when(paradaOsRepository.findAll(PAGINA)).thenReturn(new PageImpl<>(List.of(parada()), PAGINA, 1));

        // when
        PaginaResponseDTO<ParadaOsResponseDTO> resultado = service.listarTodos(PAGINA);

        // then
        assertThat(resultado.conteudo()).extracting(ParadaOsResponseDTO::id).containsExactly(1L);
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void deveListarAsParadasDaOs_quandoGerenteListaPorOs() {
        // given
        logadoComo("GERENTE");
        when(paradaOsRepository.findByOrdemServicoIdOrderByOrdemParadaAsc(3L)).thenReturn(List.of(parada()));

        // when
        List<ParadaOsResponseDTO> resultado = service.listarPorOrdemServico(3L);

        // then
        assertThat(resultado).extracting(ParadaOsResponseDTO::osId).containsExactly(3L);
    }

    @Test
    void deveLancarOsNaoEncontradaSemListar_quandoMotoristaListaParadasDaOsDeOutro() {
        // given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(6L);
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(ordemServico()));

        // when
        Throwable erro = catchThrowable(() -> service.listarPorOrdemServico(3L));

        // then
        assertThat(erro)
                .isInstanceOf(OrdemServicoNaoEncontradoException.class)
                .hasMessageContaining("3");
        verify(paradaOsRepository, never()).findByOrdemServicoIdOrderByOrdemParadaAsc(anyLong());
    }

    // ----- perfil GERENTE -----

    @Test
    void deveAlterarOCampoEGravarNaHora_quandoGerenteFazPatchDeLocal() {
        // given
        logadoComo("GERENTE");
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        // when
        ParadaOsResponseDTO resposta = service.atualizarParcial(1L, patch("Portao 9", null, null));

        // then
        assertThat(resposta.localParada()).isEqualTo("Portao 9");
        assertThat(resposta.ordemParada()).isEqualTo(1);
        assertThat(parada.getLocalParada()).isEqualTo("Portao 9");
        verify(paradaOsRepository).flush();
    }

    @Test
    void deveTrocarOsTransfersNumaConsultaSo_quandoGerenteInformaTransferIds() {
        // given
        logadoComo("GERENTE");
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada()));
        when(transferRepository.findAllById(any())).thenReturn(List.of(Transfer.builder().id(7L).build()));

        // when
        ParadaOsResponseDTO resposta = service.atualizarParcial(1L, patch(null, null, Set.of(7L)));

        // then
        assertThat(resposta.transferIds()).containsExactly(7L);
        verify(transferRepository).findAllById(any());
        verify(transferRepository, never()).findById(anyLong());
    }

    @Test
    void deveLancarTransferNaoEncontradoSemGravar_quandoUmDosTransferIdsNaoExiste() {
        // given
        logadoComo("GERENTE");
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));
        when(transferRepository.findAllById(any())).thenReturn(List.of(Transfer.builder().id(7L).build()));
        // ordem fixa (o que existe primeiro), pra o filtro do faltante passar pelos dois ids
        ParadaOsRequestDTO patch = patch(null, null, new LinkedHashSet<>(List.of(7L, 8L)));

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(1L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("8");
        verify(transferRepository, never()).findById(anyLong());
        assertThat(parada.getTransfers()).isEmpty();
        verify(paradaOsRepository, never()).flush();
    }

    // ----- parada inexistente -----

    @Test
    void deveLancarParadaNaoEncontrada_quandoBuscaIdInexistente() {
        // given
        logadoComo("GERENTE");
        when(paradaOsRepository.findById(9L)).thenReturn(Optional.empty());

        // when
        Throwable erro = catchThrowable(() -> service.buscarPorId(9L));

        // then
        assertThat(erro)
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
    }

    @Test
    void deveLancarParadaNaoEncontradaSemGravar_quandoPatchEmIdInexistente() {
        // given
        logadoComo("GERENTE");
        when(paradaOsRepository.findById(9L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO patch = patch("X", null, null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizarParcial(9L, patch));

        // then
        assertThat(erro)
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void deveLancarParadaNaoEncontradaSemApagar_quandoExcluiIdInexistente() {
        // given
        when(paradaOsRepository.existsById(9L)).thenReturn(false);

        // when
        Throwable erro = catchThrowable(() -> service.deletar(9L));

        // then
        assertThat(erro)
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
        verify(paradaOsRepository, never()).deleteById(anyLong());
    }

    @Test
    void deveApagarAParada_quandoExcluiIdExistente() {
        // given
        when(paradaOsRepository.existsById(1L)).thenReturn(true);

        // when
        service.deletar(1L);

        // then
        verify(paradaOsRepository).deleteById(1L);
    }

    // ----- cadastro e PUT -----

    @Test
    void deveLancarOsNaoEncontradaSemGravar_quandoCadastroApontaOsInexistente() {
        // given
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO dto = corpo(3L, "Portao 3", null);

        // when
        Throwable erro = catchThrowable(() -> service.criar(dto));

        // then
        assertThat(erro)
                .isInstanceOf(OrdemServicoNaoEncontradoException.class)
                .hasMessageContaining("3");
        verify(paradaOsRepository, never()).save(any());
    }

    @Test
    void deveGravarComoPendente_quandoCadastroNaoTrazStatus() {
        // given
        OrdemServico os = ordemServico();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));
        when(paradaOsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        ParadaOsResponseDTO criada = service.criar(corpo(3L, "Portao 3", null));

        // then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.PENDENTE);
        ArgumentCaptor<ParadaOs> captor = ArgumentCaptor.forClass(ParadaOs.class);
        verify(paradaOsRepository).save(captor.capture());
        ParadaOs gravada = captor.getValue();
        assertThat(gravada.getOrdemServico()).isSameAs(os);
        assertThat(gravada.getLocalParada()).isEqualTo("Portao 3");
        assertThat(gravada.getStatusParada()).isEqualTo(StatusParada.PENDENTE);
    }

    @Test
    void deveRespeitarOStatus_quandoCadastroInformaStatus() {
        // given
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(ordemServico()));
        when(paradaOsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        ParadaOsResponseDTO criada = service.criar(corpo(3L, "Portao 3", StatusParada.CONCLUIDA));

        // then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.CONCLUIDA);
    }

    @Test
    void deveLancarParadaNaoEncontradaSemGravar_quandoPutEmIdInexistente() {
        // given
        when(paradaOsRepository.findById(9L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO dto = corpo(3L, "X", null);

        // when
        Throwable erro = catchThrowable(() -> service.atualizar(9L, dto));

        // then
        assertThat(erro)
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void deveVoltarParaPendenteEGravarNaHora_quandoPutNaoTrazStatus() {
        // given
        ParadaOs parada = parada();
        parada.setStatusParada(StatusParada.CONCLUIDA);
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(ordemServico()));

        // when
        ParadaOsResponseDTO resposta = service.atualizar(1L, corpo(3L, "Portao 7", null));

        // then
        assertThat(resposta.statusParada()).isEqualTo(StatusParada.PENDENTE);
        assertThat(resposta.localParada()).isEqualTo("Portao 7");
        assertThat(parada.getLocalParada()).isEqualTo("Portao 7");
        verify(paradaOsRepository).flush();
    }
}
