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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private final ParadaOsRepository repository = mock(ParadaOsRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ParadaOsService service = new ParadaOsService(repository, ordemServicoRepository, transferRepository,
            new UsuarioLogado(), new AcessoMotorista(new UsuarioLogado(), usuarioRepository));

    private static final OrdemServico OS = OrdemServico.builder().id(3L).dataServico(LocalDate.of(2026, 10, 21))
            .motorista(Motorista.builder().id(5L).build()).build();
    private static final PageRequest PAGINA = PageRequest.of(0, 5);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComo(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void usuarioLigadoAoMotorista5() {
        when(usuarioRepository.findByUsername("usuario")).thenReturn(Optional.of(Usuario.builder()
                .username("usuario").motorista(Motorista.builder().id(5L).build()).build()));
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

    // ---------------- cadastro ----------------

    @Test
    void cadastroComOsInexistenteLanca404SemGravar() {
        // Given
        logadoComo("GERENTE");
        when(ordemServicoRepository.findById(9L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO dto = completo(9L, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroSemStatusNascePendente() {
        // Given
        logadoComo("GERENTE");
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        ParadaOsResponseDTO criada = service.criar(completo(3L, null));

        // Then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.PENDENTE);
        assertThat(criada.transferIds()).isEmpty();
        assertThat(criada.acao()).isEqualTo(AcaoParada.DESEMBARQUE);
    }

    @Test
    void cadastroRespeitaOStatusInformado() {
        // Given
        logadoComo("GERENTE");
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        ParadaOsResponseDTO criada = service.criar(comStatus(StatusParada.EM_ANDAMENTO, null));

        // Then
        assertThat(criada.statusParada()).isEqualTo(StatusParada.EM_ANDAMENTO);
    }

    // ---------------- PUT ----------------

    @Test
    void putDeParadaInexistenteLanca404() {
        // Given
        logadoComo("GERENTE");
        when(repository.findById(9L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO dto = completo(3L, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(9L, dto)).isInstanceOf(ParadaOsNaoEncontradaException.class);
    }

    @Test
    void putComVersaoVelhaLanca409() {
        // Given
        logadoComo("GERENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));
        ParadaOsRequestDTO versaoVelha = completo(3L, 1L);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, versaoVelha)).isInstanceOf(ConflitoDeVersaoException.class);
    }

    @Test
    void putParaOsInexistenteLanca404() {
        // Given
        logadoComo("GERENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));
        when(ordemServicoRepository.findById(8L)).thenReturn(Optional.empty());
        ParadaOsRequestDTO paraOs8 = completo(8L, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, paraOs8)).isInstanceOf(OrdemServicoNaoEncontradoException.class);
    }

    @Test
    void putSemStatusSubstituiOsCamposEVoltaParaPendente() {
        // Given
        logadoComo("GERENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));

        // When
        ParadaOsResponseDTO salva = service.atualizar(1L, completo(3L, 4L));

        // Then
        assertThat(salva.localParada()).isEqualTo("Hotel");
        assertThat(salva.ordemParada()).isEqualTo(2);
        assertThat(salva.statusParada()).isEqualTo(StatusParada.PENDENTE);
        verify(repository).flush();
    }

    @Test
    void putRespeitaOStatusInformado() {
        // Given
        logadoComo("GERENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));

        // When
        ParadaOsResponseDTO salva = service.atualizar(1L, comStatus(StatusParada.CONCLUIDA, 4L));

        // Then
        assertThat(salva.statusParada()).isEqualTo(StatusParada.CONCLUIDA);
    }

    // ---------------- PATCH ----------------

    @Test
    void patchDoGerenteMudaCampoACampo() {
        // Given
        logadoComo("GERENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
        when(transferRepository.findAllById(any())).thenReturn(List.of(Transfer.builder().id(7L).build()));
        ParadaOsRequestDTO patch = new ParadaOsRequestDTO(3L, 5, "Hotel B", 1.0, 2.0, LocalTime.MIDNIGHT,
                AcaoParada.EMBARQUE, StatusParada.EM_ANDAMENTO, Set.of(7L), null);

        // When
        ParadaOsResponseDTO tudo = service.atualizarParcial(1L, patch);

        // Then
        assertThat(tudo.ordemParada()).isEqualTo(5);
        assertThat(tudo.localParada()).isEqualTo("Hotel B");
        assertThat(tudo.latitude()).isEqualTo(1.0);
        assertThat(tudo.longitude()).isEqualTo(2.0);
        assertThat(tudo.horarioPrevisto()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(tudo.acao()).isEqualTo(AcaoParada.EMBARQUE);
        assertThat(tudo.statusParada()).isEqualTo(StatusParada.EM_ANDAMENTO);
        assertThat(tudo.transferIds()).containsExactly(7L);
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
    void motoristaSoPodeMudarOStatus(String campo, ParadaOsRequestDTO dto) {
        // Given
        logadoComo("MOTORISTA");

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, dto)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findById(anyLong());
    }

    // ---------------- exclusao ----------------

    @Test
    void excluirParadaExistenteApaga() {
        // Given
        logadoComo("GERENTE");
        when(repository.existsById(1L)).thenReturn(true);

        // When
        service.deletar(1L);

        // Then
        verify(repository).deleteById(1L);
    }

    @Test
    void excluirParadaInexistenteLanca404SemApagar() {
        // Given
        logadoComo("GERENTE");
        when(repository.existsById(9L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L)).isInstanceOf(ParadaOsNaoEncontradaException.class);
        verify(repository, never()).deleteById(anyLong());
    }

    // ---------------- leitura ----------------

    @Test
    void buscaDeParadaInexistenteLanca404() {
        // Given
        logadoComo("GERENTE");
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(ParadaOsNaoEncontradaException.class);
    }

    @Test
    void motoristaSemCadastroLigadoRecebeListagemVaziaSemConsultar() {
        // Given
        logadoComo("MOTORISTA");

        // When
        PaginaResponseDTO<ParadaOsResponseDTO> pagina = service.listarTodos(PAGINA);

        // Then
        assertThat(pagina.totalElementos()).isZero();
        verify(repository, never()).findAll(PAGINA);
        verify(repository, never()).findByOrdemServicoMotoristaId(anyLong(), any());
    }

    @Test
    void motoristaListaSoAsParadasDasOsDele() {
        // Given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista5();
        when(repository.findByOrdemServicoMotoristaId(5L, PAGINA)).thenReturn(new PageImpl<>(List.of(parada()), PAGINA, 1));

        // When
        PaginaResponseDTO<ParadaOsResponseDTO> pagina = service.listarTodos(PAGINA);

        // Then
        assertThat(pagina.conteudo()).extracting(ParadaOsResponseDTO::osId).containsExactly(3L);
        verify(repository, never()).findAll(PAGINA);
    }

    @Test
    void gerenteListaTodasAsParadas() {
        // Given
        logadoComo("GERENTE");
        when(repository.findAll(PAGINA)).thenReturn(new PageImpl<>(List.of(parada(), parada()), PAGINA, 2));

        // When
        PaginaResponseDTO<ParadaOsResponseDTO> pagina = service.listarTodos(PAGINA);

        // Then
        assertThat(pagina.totalElementos()).isEqualTo(2);
    }

    @Test
    void motoristaListaAsParadasDaPropriaOs() {
        // Given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista5();
        when(ordemServicoRepository.findById(3L)).thenReturn(Optional.of(OS));
        when(repository.findByOrdemServicoIdOrderByOrdemParadaAsc(3L)).thenReturn(List.of(parada()));

        // When
        List<ParadaOsResponseDTO> paradas = service.listarPorOrdemServico(3L);

        // Then
        assertThat(paradas).extracting(ParadaOsResponseDTO::localParada).containsExactly("Portao 3");
    }

    @Test
    void motoristaBuscaParadaDaPropriaOs() {
        // Given
        logadoComo("MOTORISTA");
        usuarioLigadoAoMotorista5();
        when(repository.findById(1L)).thenReturn(Optional.of(parada()));

        // When
        ParadaOsResponseDTO encontrada = service.buscarPorId(1L);

        // Then
        assertThat(encontrada.localParada()).isEqualTo("Portao 3");
    }
}
