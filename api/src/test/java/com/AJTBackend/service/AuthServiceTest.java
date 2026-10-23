package com.AJTBackend.service;

import com.AJTBackend.config.JwtService;
import com.AJTBackend.dto.LoginRequestDTO;
import com.AJTBackend.dto.LoginResponseDTO;
import com.AJTBackend.dto.TrocaSenhaRequestDTO;
import com.AJTBackend.exception.CredenciaisInvalidasException;
import com.AJTBackend.exception.MuitasTentativasException;
import com.AJTBackend.exception.RegraNegocioException;
import com.AJTBackend.exception.UsuarioNaoEncontradoException;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: login e troca de senha (AuthService) — token gerado com os
 * dados certos, username inexistente / senha errada / usuario inativo dando
 * o mesmo erro generico, bloqueio apos excesso de tentativas, e a troca de
 * senha atualizando o hash, limpando "trocarSenha" e emitindo token novo.
 *
 * como rodar: teste unitario com mocks (UsuarioRepository mockado, jwt e
 * bcrypt reais mas isolados). sem spring context, sem banco, sem docker.
 * roda com "mvn test".
 *
 * por que existe: e o fluxo de autenticacao inteiro — login, bloqueio de
 * forca bruta e troca de senha — testado sem depender de banco de dados,
 * entao roda em segundos e pega regressao de regra de negocio antes mesmo
 * de precisar do docker pros testes de integracao (ver
 * AutenticacaoIntegrationTest, que cobre o mesmo fluxo ponta a ponta).
 */
class AuthServiceTest {

    private static final String IP = "127.0.0.1";

    // BCrypt com custo baixo pra teste rapido
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final JwtService jwtService = new JwtService("chave-de-teste-com-mais-de-32-caracteres-0123456789", 3_600_000);
    private final LoginAttemptService loginAttemptService = new LoginAttemptService(3, 15);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService, loginAttemptService);
        authService.gerarHashFalso();
    }

    @Test
    void loginComSucessoGeraTokenEAtualizaUltimoLogin() {
        // Given
        Usuario usuario = usuario("admin", "senha-correta", true);
        usuario.setTrocarSenha(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        // When
        LoginResponseDTO resposta = authService.login(new LoginRequestDTO("admin", "senha-correta"), IP);

        // Then
        assertThat(resposta.token()).isNotBlank();
        assertThat(resposta.tipo()).isEqualTo("Bearer");
        assertThat(resposta.expiraEm()).isEqualTo(3600);
        assertThat(resposta.role()).isEqualTo(Role.ADMIN);
        assertThat(resposta.trocarSenha()).isTrue();
        assertThat(usuario.getUltimoLogin()).isNotNull();
        assertThat(jwtService.validarToken(resposta.token()).getSubject()).isEqualTo("admin");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void usernameInexistenteRetornaCredenciaisInvalidas() {
        // Given
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());
        LoginRequestDTO login = new LoginRequestDTO("fantasma", "qualquer");

        // When / Then
        assertThatThrownBy(() -> authService.login(login, IP)).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void senhaErradaRetornaCredenciaisInvalidas() {
        // Given
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "senha-correta", true)));
        LoginRequestDTO login = new LoginRequestDTO("admin", "errada");

        // When / Then
        assertThatThrownBy(() -> authService.login(login, IP)).isInstanceOf(CredenciaisInvalidasException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void usuarioInativoNaoLogaMesmoComSenhaCorreta() {
        // Given
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "senha-correta", false)));
        LoginRequestDTO login = new LoginRequestDTO("admin", "senha-correta");

        // When / Then
        assertThatThrownBy(() -> authService.login(login, IP)).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void loginSemUsernameRetornaCredenciaisInvalidas() {
        // Given: o @NotBlank do DTO barra isso no controller; aqui confere que o service tambem nao quebra
        when(usuarioRepository.findByUsername(null)).thenReturn(Optional.empty());
        LoginRequestDTO login = new LoginRequestDTO(null, "x");

        // When / Then
        assertThatThrownBy(() -> authService.login(login, IP)).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void tresSenhasErradasBloqueiamAteOLoginComASenhaCorreta() {
        // Given
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "senha-correta", true)));
        for (int i = 0; i < 3; i++) {
            catchThrowable(() -> authService.login(new LoginRequestDTO("admin", "errada"), IP));
        }
        LoginRequestDTO senhaCorreta = new LoginRequestDTO("admin", "senha-correta");

        // When / Then
        assertThatThrownBy(() -> authService.login(senhaCorreta, IP)).isInstanceOf(MuitasTentativasException.class);
    }

    @Test
    void usuarioBloqueadoNemConsultaOBanco() {
        // Given
        for (int i = 0; i < 3; i++) {
            loginAttemptService.registrarFalha(IP, "admin");
        }
        LoginRequestDTO login = new LoginRequestDTO("admin", "x");

        // When / Then
        assertThatThrownBy(() -> authService.login(login, IP)).isInstanceOf(MuitasTentativasException.class);
        verify(usuarioRepository, never()).findByUsername(anyString());
    }

    @Test
    void trocaDeSenhaAtualizaHashLimpaFlagEMarcaData() {
        // Given
        Usuario usuario = usuario("admin", "senha-antiga", true);
        usuario.setTrocarSenha(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        // When
        LoginResponseDTO resposta = authService.trocarSenha("admin", new TrocaSenhaRequestDTO("senha-antiga", "senha-nova-123"));

        // Then
        assertThat(passwordEncoder.matches("senha-nova-123", usuario.getSenha())).isTrue();
        assertThat(usuario.getTrocarSenha()).isFalse();
        assertThat(usuario.getSenhaAlteradaEm()).isNotNull();
        assertThat(resposta.trocarSenha()).isFalse();
        // o token novo continua valido depois da troca
        assertThat(jwtService.emitidoAntesDe(jwtService.validarToken(resposta.token()), usuario.getSenhaAlteradaEm())).isFalse();
    }

    @Test
    void trocaDeSenhaComSenhaAtualErradaNaoAlteraNada() {
        // Given
        Usuario usuario = usuario("admin", "senha-antiga", true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
        String hashAntes = usuario.getSenha();
        TrocaSenhaRequestDTO troca = new TrocaSenhaRequestDTO("errada", "senha-nova-123");

        // When / Then
        assertThatThrownBy(() -> authService.trocarSenha("admin", troca))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Senha atual incorreta");
        assertThat(usuario.getSenha()).isEqualTo(hashAntes);
    }

    @Test
    void novaSenhaNaoPodeSerIgualAAtual() {
        // Given
        Usuario usuario = usuario("admin", "senha-antiga", true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
        String hashAntes = usuario.getSenha();
        TrocaSenhaRequestDTO troca = new TrocaSenhaRequestDTO("senha-antiga", "senha-antiga");

        // When / Then
        assertThatThrownBy(() -> authService.trocarSenha("admin", troca))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("diferente da atual");
        assertThat(usuario.getSenha()).isEqualTo(hashAntes);
    }

    @Test
    void trocaDeSenhaDeUsuarioRemovidoRetorna404() {
        // Given
        when(usuarioRepository.findByUsername("removido")).thenReturn(Optional.empty());
        TrocaSenhaRequestDTO troca = new TrocaSenhaRequestDTO("a", "senha-nova-123");

        // When / Then
        assertThatThrownBy(() -> authService.trocarSenha("removido", troca))
                .isInstanceOf(UsuarioNaoEncontradoException.class);
    }

    private Usuario usuario(String username, String senha, boolean ativo) {
        return Usuario.builder()
                .id(1L).nome("Admin").username(username)
                .senha(passwordEncoder.encode(senha))
                .role(Role.ADMIN).ativo(ativo)
                .build();
    }
}
