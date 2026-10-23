package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.exception.MotoristaNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.UsernameJaCadastradoException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final MotoristaRepository motoristaRepository = mock(MotoristaRepository.class);
    private final UsuarioService service = new UsuarioService(usuarioRepository, passwordEncoder, new UsuarioLogado(), motoristaRepository);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void logadoComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private static Usuario usuario(Long id, String username, Role role) {
        return Usuario.builder().id(id).nome("Nome").username(username).senha("hash").role(role)
                .ativo(true).trocarSenha(false).build();
    }

    private static UsuarioRequestDTO cadastro(String username, String senha, Role role, Long motoristaId) {
        return new UsuarioRequestDTO("Nome", username, senha, role, null, motoristaId, null);
    }

    private static UsuarioRequestDTO patchSenha(String senha) {
        return new UsuarioRequestDTO(null, null, senha, null, null, null, null);
    }

    private static UsuarioRequestDTO patchRole(Role role) {
        return new UsuarioRequestDTO(null, null, null, role, null, null, null);
    }

    private static UsuarioRequestDTO patchAtivo(boolean ativo) {
        return new UsuarioRequestDTO(null, null, null, null, ativo, null, null);
    }

    private Usuario gravadoPeloSave() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }

    // ----- cadastro -----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void criarSemSenhaLancaRegraDeNegocioSemGravar(String senha) {
        // Given
        UsuarioRequestDTO dto = cadastro("maria", senha, Role.ATENDENTE, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Senha");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criarGravaOHashDaSenhaEnuncaOTextoPuro() {
        // Given
        when(usuarioRepository.existsByUsername("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.criar(cadastro("maria", "senha123", Role.ATENDENTE, null));

        // Then
        Usuario gravado = gravadoPeloSave();
        assertThat(gravado.getSenha()).isNotEqualTo("senha123");
        assertThat(passwordEncoder.matches("senha123", gravado.getSenha())).isTrue();
    }

    @Test
    void criarGravaOsDadosInformadosEObrigaATrocaDeSenhaNoPrimeiroAcesso() {
        // Given
        when(usuarioRepository.existsByUsername("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        UsuarioResponseDTO criado = service.criar(cadastro("maria", "senha123", Role.ATENDENTE, null));

        // Then
        assertThat(criado.username()).isEqualTo("maria");
        assertThat(criado.role()).isEqualTo(Role.ATENDENTE);
        assertThat(criado.trocarSenha()).isTrue();
        assertThat(criado.ativo()).isTrue();
        assertThat(criado.motoristaId()).isNull();
    }

    @Test
    void criarComAtivoFalsoGravaOUsuarioInativo() {
        // Given
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        UsuarioRequestDTO dto = new UsuarioRequestDTO("Maria", "maria", "senha123", Role.ATENDENTE, false, null, null);

        // When
        UsuarioResponseDTO criado = service.criar(dto);

        // Then
        assertThat(criado.ativo()).isFalse();
        assertThat(gravadoPeloSave().getAtivo()).isFalse();
    }

    @Test
    void criarComUsernameDuplicadoLancaExcecaoSemGravar() {
        // Given
        when(usuarioRepository.existsByUsername("maria")).thenReturn(true);
        UsuarioRequestDTO dto = cadastro("maria", "senha123", Role.ATENDENTE, null);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(UsernameJaCadastradoException.class)
                .hasMessageContaining("maria");
        verify(usuarioRepository, never()).save(any());
    }

    // ----- username no PUT -----

    @Test
    void putNaoPermiteTrocarParaUsernameDeOutroUsuario() {
        // Given
        Usuario maria = usuario(1L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(maria));
        when(usuarioRepository.existsByUsername("joao")).thenReturn(true);
        UsuarioRequestDTO dto = new UsuarioRequestDTO("Maria", "joao", null, Role.ATENDENTE, null, null, null);

        // When / Then
        assertThatThrownBy(() -> service.atualizar(1L, dto))
                .isInstanceOf(UsernameJaCadastradoException.class)
                .hasMessageContaining("joao");
        assertThat(maria.getUsername()).isEqualTo("maria");
        verify(usuarioRepository, never()).flush();
    }

    // ----- senha redefinida -----

    @Test
    void adminRedefinindoSenhaDeOutroUsuarioDerrubaSessoesEObrigaTroca() {
        // Given
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        // When
        service.atualizarParcial(2L, patchSenha("nova-senha-1"));

        // Then
        assertThat(maria.getSenhaAlteradaEm()).isNotNull();
        assertThat(maria.getTrocarSenha()).isTrue();
        assertThat(passwordEncoder.matches("nova-senha-1", maria.getSenha())).isTrue();
        verify(usuarioRepository).flush();
    }

    @Test
    void usuarioRedefinindoAPropriaSenhaNaoPrecisaTrocarDeNovo() {
        // Given
        logadoComo("maria");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        maria.setTrocarSenha(true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        // When
        service.atualizarParcial(2L, patchSenha("nova-senha-1"));

        // Then
        assertThat(maria.getTrocarSenha()).isFalse();
        assertThat(maria.getSenhaAlteradaEm()).isNotNull();
        assertThat(passwordEncoder.matches("nova-senha-1", maria.getSenha())).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void patchSemSenhaOuComSenhaEmBrancoMantemASenhaAtual(String senha) {
        // Given
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        // When
        service.atualizarParcial(2L, patchSenha(senha));

        // Then
        assertThat(maria.getSenha()).isEqualTo("hash");
        assertThat(maria.getSenhaAlteradaEm()).isNull();
        assertThat(maria.getTrocarSenha()).isFalse();
    }

    // ----- travas de auto-protecao -----

    @Test
    void naoPodeExcluirOProprioUsuario() {
        // Given
        logadoComo("admin");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "admin", Role.ADMIN)));

        // When / Then
        assertThatThrownBy(() -> service.deletar(1L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio usuário");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void adminPodeExcluirOutroUsuario() {
        // Given
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        // When
        service.deletar(2L);

        // Then
        verify(usuarioRepository).delete(maria);
    }

    @Test
    void excluirUsuarioInexistenteLanca404SemApagarNada() {
        // Given
        logadoComo("admin");
        when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.deletar(9L))
                .isInstanceOf(UsuarioNaoEncontradoException.class)
                .hasMessageContaining("9");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void naoPodeRebaixarOProprioPerfil() {
        // Given
        logadoComo("admin");
        Usuario admin = usuario(1L, "admin", Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        UsuarioRequestDTO rebaixar = patchRole(Role.ATENDENTE);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, rebaixar))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio perfil");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        verify(usuarioRepository, never()).flush();
    }

    @Test
    void mandarOMesmoPerfilDoProprioUsuarioNaoEhAlteracaoENaoLancaErro() {
        // Given
        logadoComo("admin");
        Usuario admin = usuario(1L, "admin", Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        // When
        UsuarioResponseDTO resposta = service.atualizarParcial(1L, patchRole(Role.ADMIN));

        // Then
        assertThat(resposta.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void adminPodeMudarOPerfilDeOutroUsuario() {
        // Given
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        // When
        UsuarioResponseDTO resposta = service.atualizarParcial(2L, patchRole(Role.ADMIN));

        // Then
        assertThat(resposta.role()).isEqualTo(Role.ADMIN);
        assertThat(maria.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void naoPodeDesativarOProprioUsuario() {
        // Given
        logadoComo("admin");
        Usuario admin = usuario(1L, "admin", Role.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        UsuarioRequestDTO desativar = patchAtivo(false);

        // When / Then
        assertThatThrownBy(() -> service.atualizarParcial(1L, desativar))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desativar");
        assertThat(admin.getAtivo()).isTrue();
        verify(usuarioRepository, never()).flush();
    }

    @Test
    void adminPodeDesativarOutroUsuario() {
        // Given
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        // When
        UsuarioResponseDTO resposta = service.atualizarParcial(2L, patchAtivo(false));

        // Then
        assertThat(resposta.ativo()).isFalse();
        assertThat(maria.getAtivo()).isFalse();
    }

    // ----- HU07: vinculo com o cadastro de motorista -----

    @Test
    void criaUsuarioMotoristaLigadoAoCadastroDeMotorista() {
        // Given
        Motorista carlos = Motorista.builder().id(5L).nome("Carlos").build();
        when(usuarioRepository.existsByUsername("carlos")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(carlos));
        when(usuarioRepository.findByMotoristaId(5L)).thenReturn(Optional.empty());
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UsuarioResponseDTO criado = service.criar(cadastro("carlos", "senha123", Role.MOTORISTA, 5L));

        // Then
        assertThat(criado.motoristaId()).isEqualTo(5L);
        assertThat(gravadoPeloSave().getMotorista()).isSameAs(carlos);
    }

    @Test
    void soPerfilMotoristaPodeSerLigadoAUmMotorista() {
        // Given
        when(usuarioRepository.existsByUsername("ana")).thenReturn(false);
        UsuarioRequestDTO dto = cadastro("ana", "senha123", Role.ATENDENTE, 5L);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("perfil Motorista");
        verify(motoristaRepository, never()).findById(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void ligarAUmMotoristaInexistenteLanca404SemGravar() {
        // Given
        when(motoristaRepository.findById(5L)).thenReturn(Optional.empty());
        UsuarioRequestDTO dto = cadastro("carlos", "senha123", Role.MOTORISTA, 5L);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(MotoristaNaoEncontradoException.class)
                .hasMessageContaining("5");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastroDeMotoristaSoPodeTerUmUsuario() {
        // Given
        when(usuarioRepository.existsByUsername("carlos2")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).nome("Carlos").build()));
        when(usuarioRepository.findByMotoristaId(5L))
                .thenReturn(Optional.of(Usuario.builder().id(3L).username("carlos").build()));
        UsuarioRequestDTO dto = cadastro("carlos2", "senha123", Role.MOTORISTA, 5L);

        // When / Then
        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("carlos");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void reenviarOMotoristaDoProprioUsuarioNaoContaComoConflito() {
        // Given
        Motorista carlos = Motorista.builder().id(5L).nome("Carlos").build();
        Usuario usuario = usuario(2L, "carlos", Role.MOTORISTA);
        usuario.setMotorista(carlos);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(carlos));
        when(usuarioRepository.findByMotoristaId(5L)).thenReturn(Optional.of(usuario));
        UsuarioRequestDTO patch = new UsuarioRequestDTO(null, null, null, null, null, 5L, null);

        // When
        UsuarioResponseDTO resposta = service.atualizarParcial(2L, patch);

        // Then
        assertThat(resposta.motoristaId()).isEqualTo(5L);
    }

    @Test
    void deixarDeSerMotoristaDesfazOVinculo() {
        // Given
        Usuario usuario = usuario(2L, "carlos", Role.MOTORISTA);
        usuario.setMotorista(Motorista.builder().id(5L).build());
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));

        // When
        UsuarioResponseDTO atualizado = service.atualizarParcial(2L, patchRole(Role.ATENDENTE));

        // Then
        assertThat(atualizado.motoristaId()).isNull();
        assertThat(atualizado.role()).isEqualTo(Role.ATENDENTE);
        assertThat(usuario.getMotorista()).isNull();
    }
}