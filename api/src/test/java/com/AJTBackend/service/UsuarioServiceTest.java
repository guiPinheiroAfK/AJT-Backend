package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.UsernameJaCadastradoException;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.MotoristaRepository;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

/*
 * o que testa: as regras de gestao de usuario (UsuarioService) — senha
 * obrigatoria no cadastro, hash gravado (nunca a senha em texto puro),
 * username duplicado bloqueado no put/patch, admin redefinindo a senha de
 * outro usuario derruba sessao e obriga troca, e as travas de "nao pode
 * mexer na propria conta" (excluir, rebaixar o proprio perfil, se desativar).
 *
 * como rodar: teste unitario com mocks (UsuarioRepository mockado; o
 * "usuario logado" e simulado escrevendo direto no SecurityContextHolder).
 * sem spring context, sem banco, sem docker. roda com "mvn test".
 *
 * por que existe: as travas de auto-protecao (nao excluir/rebaixar/desativar
 * a si mesmo) sao regra de negocio pura, sem depender de http nem do banco —
 * testar aqui pega regressao rapido, sem precisar do docker do
 * PersistenciaIntegrationTest.
 */
class UsuarioServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final MotoristaRepository motoristaRepository = mock(MotoristaRepository.class);
    private final UsuarioService service = new UsuarioService(usuarioRepository, passwordEncoder, new UsuarioLogado(), motoristaRepository);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criarExigeSenha() {
        assertThatThrownBy(() -> service.criar(new UsuarioRequestDTO("Maria", "maria", null, Role.ATENDENTE, null, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Senha");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void criarGuardaHashEObrigaTrocaDeSenha() {
        when(usuarioRepository.existsByUsername("maria")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponseDTO criado = service.criar(new UsuarioRequestDTO("Maria", "maria", "senha123", Role.ATENDENTE, null, null, null));

        assertThat(criado.trocarSenha()).isTrue();
        assertThat(criado.ativo()).isTrue();
        verify(usuarioRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                !u.getSenha().equals("senha123") && passwordEncoder.matches("senha123", u.getSenha())));
    }

    @Test
    void criarComUsernameDuplicado() {
        when(usuarioRepository.existsByUsername("maria")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new UsuarioRequestDTO("Maria", "maria", "senha123", Role.ATENDENTE, null, null, null)))
                .isInstanceOf(UsernameJaCadastradoException.class);
    }

    @Test
    void putNaoPermiteTrocarParaUsernameDeOutroUsuario() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "maria", Role.ATENDENTE)));
        when(usuarioRepository.existsByUsername("joao")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(1L, new UsuarioRequestDTO("Maria", "joao", null, Role.ATENDENTE, null, null, null)))
                .isInstanceOf(UsernameJaCadastradoException.class);
    }

    @Test
    void adminRedefinindoSenhaDerrubaSessoesEObrigaTroca() {
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        service.atualizarParcial(2L, new UsuarioRequestDTO(null, null, "nova-senha-1", null, null, null, null));

        assertThat(maria.getSenhaAlteradaEm()).isNotNull();
        assertThat(maria.getTrocarSenha()).isTrue();
        assertThat(passwordEncoder.matches("nova-senha-1", maria.getSenha())).isTrue();
    }

    @Test
    void naoPodeExcluirOProprioUsuario() {
        logadoComo("admin");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "admin", Role.ADMIN)));

        assertThatThrownBy(() -> service.deletar(1L)).isInstanceOf(RegraNegocioException.class);
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void naoPodeRebaixarOProprioPerfil() {
        logadoComo("admin");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "admin", Role.ADMIN)));

        assertThatThrownBy(() -> service.atualizarParcial(1L, new UsuarioRequestDTO(null, null, null, Role.ATENDENTE, null, null, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void naoPodeDesativarOProprioUsuario() {
        logadoComo("admin");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "admin", Role.ADMIN)));

        assertThatThrownBy(() -> service.atualizarParcial(1L, new UsuarioRequestDTO(null, null, null, null, false, null, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void adminPodeDesativarOutroUsuario() {
        logadoComo("admin");
        Usuario maria = usuario(2L, "maria", Role.ATENDENTE);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        assertThat(service.atualizarParcial(2L, new UsuarioRequestDTO(null, null, null, null, false, null, null)).ativo()).isFalse();
    }

    private static void logadoComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private static Usuario usuario(Long id, String username, Role role) {
        return Usuario.builder().id(id).nome("Nome").username(username).senha("hash").role(role).build();
    }

    // ----- HU07: vinculo com o cadastro de motorista -----

    @Test
    void criaMotoristaLigadoAoCadastroDeMotorista() {
        when(usuarioRepository.existsByUsername("carlos")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).nome("Carlos").build()));
        when(usuarioRepository.findByMotoristaId(5L)).thenReturn(Optional.empty());
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponseDTO criado = service.criar(new UsuarioRequestDTO("Carlos", "carlos", "senha123", Role.MOTORISTA, null, 5L, null));

        assertThat(criado.motoristaId()).isEqualTo(5L);
    }

    @Test
    void soPerfilMotoristaPodeSerLigadoAUmMotorista() {
        when(usuarioRepository.existsByUsername("ana")).thenReturn(false);

        assertThatThrownBy(() -> service.criar(new UsuarioRequestDTO("Ana", "ana", "senha123", Role.ATENDENTE, null, 5L, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("perfil Motorista");
    }

    @Test
    void cadastroDeMotoristaSoPodeTerUmUsuario() {
        when(usuarioRepository.existsByUsername("carlos2")).thenReturn(false);
        when(motoristaRepository.findById(5L)).thenReturn(Optional.of(Motorista.builder().id(5L).nome("Carlos").build()));
        when(usuarioRepository.findByMotoristaId(5L)).thenReturn(Optional.of(Usuario.builder().id(3L).username("carlos").build()));

        assertThatThrownBy(() -> service.criar(new UsuarioRequestDTO("Carlos", "carlos2", "senha123", Role.MOTORISTA, null, 5L, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("carlos");
    }

    @Test
    void deixarDeSerMotoristaDesfazOVinculo() {
        Usuario usuario = Usuario.builder().id(2L).username("carlos").role(Role.MOTORISTA).ativo(true)
                .motorista(Motorista.builder().id(5L).build()).build();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO atualizado = service.atualizarParcial(2L, new UsuarioRequestDTO(null, null, null, Role.ATENDENTE, null, null, null));

        assertThat(atualizado.motoristaId()).isNull();
    }
}
