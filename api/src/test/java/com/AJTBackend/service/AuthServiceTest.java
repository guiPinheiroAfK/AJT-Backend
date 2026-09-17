package com.AJTBackend.service;

import com.AJTBackend.config.JwtService;
import com.AJTBackend.dto.LoginRequestDTO;
import com.AJTBackend.dto.LoginResponseDTO;
import com.AJTBackend.dto.TrocaSenhaRequestDTO;
import com.AJTBackend.exception.CredenciaisInvalidasException;
import com.AJTBackend.exception.MuitasTentativasException;
import com.AJTBackend.exception.RegraNegocioException;
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
        Usuario usuario = usuario("admin", "senha-correta", true);
        usuario.setTrocarSenha(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        LoginResponseDTO resposta = authService.login(new LoginRequestDTO("admin", "senha-correta"), IP);

        assertThat(resposta.token()).isNotBlank();
        assertThat(resposta.tipo()).isEqualTo("Bearer");
        assertThat(resposta.expiraEm()).isEqualTo(3600);
        assertThat(resposta.role()).isEqualTo(Role.ADMIN);
        assertThat(resposta.trocarSenha()).isTrue();
        assertThat(usuario.getUltimoLogin()).isNotNull();
        assertThat(jwtService.validarToken(resposta.token()).getSubject()).isEqualTo("admin");
    }

    @Test
    void usernameInexistenteRetornaCredenciaisInvalidas() {
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("fantasma", "qualquer"), IP))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void senhaErradaRetornaCredenciaisInvalidas() {
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "senha-correta", true)));

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("admin", "errada"), IP))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void usuarioInativoNaoLoga() {
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "senha-correta", false)));

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("admin", "senha-correta"), IP))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void bloqueiaAposExcessoDeTentativasMesmoComSenhaCorreta() {
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "senha-correta", true)));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequestDTO("admin", "errada"), IP))
                    .isInstanceOf(CredenciaisInvalidasException.class);
        }

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("admin", "senha-correta"), IP))
                .isInstanceOf(MuitasTentativasException.class);
    }

    @Test
    void trocaDeSenhaAtualizaHashLimpaFlagEMarcaData() {
        Usuario usuario = usuario("admin", "senha-antiga", true);
        usuario.setTrocarSenha(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        LoginResponseDTO resposta = authService.trocarSenha("admin", new TrocaSenhaRequestDTO("senha-antiga", "senha-nova-123"));

        assertThat(passwordEncoder.matches("senha-nova-123", usuario.getSenha())).isTrue();
        assertThat(usuario.getTrocarSenha()).isFalse();
        assertThat(usuario.getSenhaAlteradaEm()).isNotNull();
        assertThat(resposta.trocarSenha()).isFalse();
        // o token novo continua valido depois da troca
        assertThat(jwtService.emitidoAntesDe(jwtService.validarToken(resposta.token()), usuario.getSenhaAlteradaEm())).isFalse();
    }

    @Test
    void trocaDeSenhaComSenhaAtualErrada() {
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "senha-antiga", true)));

        assertThatThrownBy(() -> authService.trocarSenha("admin", new TrocaSenhaRequestDTO("errada", "senha-nova-123")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Senha atual incorreta");
    }

    @Test
    void novaSenhaNaoPodeSerIgualAAtual() {
        Usuario usuario = usuario("admin", "senha-antiga", true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
        String hashAntes = usuario.getSenha();

        assertThatThrownBy(() -> authService.trocarSenha("admin", new TrocaSenhaRequestDTO("senha-antiga", "senha-antiga")))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(usuario.getSenha()).isEqualTo(hashAntes);
    }

    @Test
    void usuarioBloqueadoNemConsultaOBanco() {
        for (int i = 0; i < 3; i++) {
            loginAttemptService.registrarFalha(IP, "admin");
        }

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO("admin", "x"), IP))
                .isInstanceOf(MuitasTentativasException.class);
        verify(usuarioRepository, never()).findByUsername(anyString());
    }

    private Usuario usuario(String username, String senha, boolean ativo) {
        return Usuario.builder()
                .id(1L).nome("Admin").username(username)
                .senha(passwordEncoder.encode(senha))
                .role(Role.ADMIN).ativo(ativo)
                .build();
    }
}
