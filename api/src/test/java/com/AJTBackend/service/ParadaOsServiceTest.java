package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.ParadaOsRequestDTO;
import com.AJTBackend.dto.ParadaOsResponseDTO;
import com.AJTBackend.exception.OrdemServicoNaoEncontradoException;
import com.AJTBackend.exception.ParadaOsNaoEncontradaException;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.StatusParada;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.AJTBackend.repository.TransferRepository;
import com.AJTBackend.repository.UsuarioRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParadaOsServiceTest {

    private final ParadaOsRepository paradaOsRepository = mock(ParadaOsRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ParadaOsService service = new ParadaOsService(paradaOsRepository, ordemServicoRepository, transferRepository,
            new UsuarioLogado(), new AcessoMotorista(new UsuarioLogado(), usuarioRepository));

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
    void motoristaPodeAtualizarSoOStatusDaParada() {
        // Given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(5L);
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        // When
        ParadaOsResponseDTO resposta = service.atualizarParcial(1L, patch(null, StatusParada.CONCLUIDA, null));

        // Then
        assertThat(resposta.statusParada()).isEqualTo(StatusParada.CONCLUIDA);
        assertThat(parada.getStatusParada()).isEqualTo(StatusParada.CONCLUIDA);
        assertThat(parada.getLocalParada()).isEqualTo("Portao 3");
        verify(paradaOsRepository).flush();
    }

    @ParameterizedTest
    @MethodSource("patchesQueMexemEmOutrosCampos")
    void motoristaNaoPodeAlterarOutrosCamposDaParada(ParadaOsRequestDTO patch) {
        // Given
        logadoComo("MOTORISTA");

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, patch))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("só pode alterar o status");
        verify(paradaOsRepository, never()).findById(anyLong());
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void motoristaNaoMexeEmParadaDeOsDeOutroMotorista() {
        // Given: 404 e nao 403, o motorista nem fica sabendo que a parada existe (HU07)
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(6L);
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, patch(null, StatusParada.CONCLUIDA, null)))
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("1");
        assertThat(parada.getStatusParada()).isNotEqualTo(StatusParada.CONCLUIDA);
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void motoristaSemCadastroLigadoNaoVeParadas() {
        // Given
        logadoComo("MOTORISTA");
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada()));

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(1L))
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("1");
    }

    @Test
    void motoristaVeParadaDaPropriaOs() {
        // Given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(5L);
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada()));

        // When
        ParadaOsResponseDTO resposta = service.buscarPorId(1L);

        // Then
        assertThat(resposta.id()).isEqualTo(1L);
        assertThat(resposta.osId()).isEqualTo(3L);
        assertThat(resposta.localParada()).isEqualTo("Portao 3");
    }

    // ----- listagens -----

    @Test
    void motoristaListaSoAsParadasDasProprias() {
        // Given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(5L);
        Pageable pagina = PageRequest.of(0, 5);
        when(paradaOsRepository.findByOrdemServicoMotoristaId(5L, pagina))
                .thenReturn(new PageImpl<>(List.of(parada()), pagina, 1));

        // When
        PaginaResponseDTO<ParadaOsResponseDTO> resultado = service.listarTodos(pagina);

        // Then
        assertThat(resultado.conteudo()).extracting(ParadaOsResponseDTO::id).containsExactly(1L);
        verify(paradaOsRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void motoristaSemCadastroLigadoRecebeListaVazia() {
        // Given
        logadoComo("MOTORISTA");
        Pageable pagina = PageRequest.of(0, 5);

        // When
        PaginaResponseDTO<ParadaOsResponseDTO> resultado = service.listarTodos(pagina);

        // Then
        assertThat(resultado.conteudo()).isEmpty();
        verify(paradaOsRepository, never()).findAll(any(Pageable.class));
        verify(paradaOsRepository, never()).findByOrdemServicoMotoristaId(anyLong(), any(Pageable.class));
    }

    @Test
    void gerenteListaTodasAsParadas() {
        // Given
        logadoComo("GERENTE");
        Pageable pagina = PageRequest.of(0, 5);
        when(paradaOsRepository.findAll(pagina)).thenReturn(new PageImpl<>(List.of(parada()), pagina, 1));

        // When
        PaginaResponseDTO<ParadaOsResponseDTO> resultado = service.listarTodos(pagina);

        // Then
        assertThat(resultado.conteudo()).extracting(ParadaOsResponseDTO::id).containsExactly(1L);
        assertThat(resultado.totalElementos()).isEqualTo(1);
    }

    @Test
    void gerenteListaAsParadasDeUmaOsEmOrdem() {
        // Given
        logadoComo("GERENTE");
        when(paradaOsRepository.findByOrdemServicoIdOrderByOrdemParadaAsc(3L)).thenReturn(List.of(parada()));

        // When
        List<ParadaOsResponseDTO> resultado = service.listarPorOrdemServico(3L);

        // Then
        assertThat(resultado).extracting(ParadaOsResponseDTO::osId).containsExactly(3L);
    }

    @Test
    void motoristaNaoListaParadasDeOsDeOutroMotorista() {
        // Given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista(6L);
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(ordemServico()));

        // When / Then
        assertThatThrownBy(() -> service.listarPorOrdemServico(3L))
                .isInstanceOf(OrdemServicoNaoEncontradoException.class)
                .hasMessageContaining("3");
        verify(paradaOsRepository, never()).findByOrdemServicoIdOrderByOrdemParadaAsc(anyLong());
    }

    // ----- perfil GERENTE -----

    @Test
    void gerentePodeAlterarQualquerCampo() {
        // Given
        logadoComo("GERENTE");
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        // When
        ParadaOsResponseDTO resposta = service.atualizarParcial(1L, patch("Portao 9", null, null));

        // Then
        assertThat(resposta.localParada()).isEqualTo("Portao 9");
        assertThat(resposta.ordemParada()).isEqualTo(1);
        assertThat(parada.getLocalParada()).isEqualTo("Portao 9");
        verify(paradaOsRepository).flush();
    }

    @Test
    void gerenteTrocaOsTransfersDaParadaComUmaUnicaConsulta() {
        // Given
        logadoComo("GERENTE");
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada()));
        when(transferRepository.findAllById(any())).thenReturn(List.of(Transfer.builder().id(7L).build()));

        // When
        ParadaOsResponseDTO resposta = service.atualizarParcial(1L, patch(null, null, Set.of(7L)));

        // Then
        assertThat(resposta.transferIds()).containsExactly(7L);
        verify(transferRepository).findAllById(any());
        verify(transferRepository, never()).findById(anyLong());
    }

    @Test
    void buscaTransfersNumaUnicaConsultaEDetectaIdInexistente() {
        // Given
        logadoComo("GERENTE");
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));
        when(transferRepository.findAllById(any())).thenReturn(List.of(Transfer.builder().id(7L).build()));
        ParadaOsRequestDTO patch = patch(null, null, Set.of(7L, 8L));

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, patch))
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("8");
        verify(transferRepository, never()).findById(anyLong());
        assertThat(parada.getTransfers()).isEmpty();
        verify(paradaOsRepository, never()).flush();
    }

    // ----- parada inexistente -----

    @Test
    void buscarPorIdInexistenteLanca404() {
        // Given
        logadoComo("GERENTE");
        when(paradaOsRepository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L))
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
    }

    @Test
    void atualizarParcialInexistenteLanca404SemGravar() {
        // Given
        logadoComo("GERENTE");
        when(paradaOsRepository.findById(9L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO patch = patch("X", null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(9L, patch))
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void deletarInexistenteLanca404SemApagarNada() {
        // Given
        when(paradaOsRepository.existsById(9L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L))
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
        verify(paradaOsRepository, never()).deleteById(anyLong());
    }

    @Test
    void deletarExistenteApagaAParada() {
        // Given
        when(paradaOsRepository.existsById(1L)).thenReturn(true);

        // When
        service.deletar(1L);

        // Then
        verify(paradaOsRepository).deleteById(1L);
    }

    // ----- cadastro e PUT -----

    @Test
    void criarComOsInexistenteLanca404SemGravar() {
        // Given
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO dto = corpo(3L, "Portao 3", null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(OrdemServicoNaoEncontradoException.class)
                .hasMessageContaining("3");
        verify(paradaOsRepository, never()).save(any());
    }

    @Test
    void criarSemStatusGravaComoPendente() {
        // Given
        OrdemServico os = ordemServico();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(os));
        when(paradaOsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        ParadaOsResponseDTO criada = service.criar(corpo(3L, "Portao 3", null));

        // Then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.PENDENTE);
        ArgumentCaptor<ParadaOs> captor = ArgumentCaptor.forClass(ParadaOs.class);
        verify(paradaOsRepository).save(captor.capture());
        ParadaOs gravada = captor.getValue();
        assertThat(gravada.getOrdemServico()).isSameAs(os);
        assertThat(gravada.getLocalParada()).isEqualTo("Portao 3");
        assertThat(gravada.getStatusParada()).isEqualTo(StatusParada.PENDENTE);
    }

    @Test
    void criarComStatusInformadoRespeitaOStatus() {
        // Given
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(ordemServico()));
        when(paradaOsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        ParadaOsResponseDTO criada = service.criar(corpo(3L, "Portao 3", StatusParada.CONCLUIDA));

        // Then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.CONCLUIDA);
    }

    @Test
    void putInexistenteLanca404SemGravar() {
        // Given
        when(paradaOsRepository.findById(9L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO dto = corpo(3L, "X", null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto))
                .isInstanceOf(ParadaOsNaoEncontradaException.class)
                .hasMessageContaining("9");
        verify(paradaOsRepository, never()).flush();
    }

    @Test
    void putSemStatusVoltaAParadaParaPendenteEGravaAgora() {
        // Given
        ParadaOs parada = parada();
        parada.setStatusParada(StatusParada.CONCLUIDA);
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(ordemServico()));

        // When
        ParadaOsResponseDTO resposta = service.atualizar(1L, corpo(3L, "Portao 7", null));

        // Then
        assertThat(resposta.statusParada()).isEqualTo(StatusParada.PENDENTE);
        assertThat(resposta.localParada()).isEqualTo("Portao 7");
        assertThat(parada.getLocalParada()).isEqualTo("Portao 7");
        verify(paradaOsRepository).flush();
    }
}