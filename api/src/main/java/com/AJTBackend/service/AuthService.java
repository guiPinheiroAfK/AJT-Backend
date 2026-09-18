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
import com.AJTBackend.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    // Hash usado quando o username nao existe, pra resposta levar o mesmo tempo
    // e nao revelar quais usernames sao validos (timing attack)
    private String hashFalso;

    @PostConstruct
    void gerarHashFalso() {
        hashFalso = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // sem @Transactional de proposito: o BCrypt (~100ms) nao deve segurar uma conexao do pool.
    // so o save() do ultimoLogin abre uma transacao curta.
    public LoginResponseDTO login(LoginRequestDTO dto, String ip) {
        if (loginAttemptService.bloqueado(ip, dto.username())) {
            log.warn("Login bloqueado por excesso de tentativas: usuario={}, ip={}", sanitizar(dto.username()), ip);
            throw new MuitasTentativasException();
        }

        Optional<Usuario> encontrado = usuarioRepository.findByUsername(dto.username());
        boolean senhaConfere = passwordEncoder.matches(dto.senha(),
                encontrado.map(Usuario::getSenha).orElse(hashFalso));

        if (encontrado.isEmpty() || !senhaConfere || !Boolean.TRUE.equals(encontrado.get().getAtivo())) {
            loginAttemptService.registrarFalha(ip, dto.username());
            log.warn("Falha de login: usuario={}, ip={}, motivo={}", sanitizar(dto.username()), ip,
                    encontrado.isEmpty() ? "inexistente" : !senhaConfere ? "senha incorreta" : "inativo");
            throw new CredenciaisInvalidasException();
        }

        Usuario usuario = encontrado.get();
        loginAttemptService.registrarSucesso(ip, dto.username());
        usuario.setUltimoLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        log.info("Login bem-sucedido: usuario={}, role={}", usuario.getUsername(), usuario.getRole());
        return gerarResposta(usuario);
    }

    /**
     * Troca a senha do proprio usuario. Tokens emitidos antes da troca deixam
     * de valer, entao ja devolve um token novo.
     */
    @Transactional
    public LoginResponseDTO trocarSenha(String username, TrocaSenhaRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(username));

        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenha())) {
            log.warn("Troca de senha com senha atual incorreta: usuario={}", username);
            throw new RegraNegocioException("Senha atual incorreta");
        }
        if (dto.senhaAtual().equals(dto.novaSenha())) {
            throw new RegraNegocioException("A nova senha deve ser diferente da atual");
        }

        usuario.setSenha(passwordEncoder.encode(dto.novaSenha()));
        usuario.setTrocarSenha(false);
        usuario.setSenhaAlteradaEm(LocalDateTime.now());

        log.info("Senha alterada pelo proprio usuario: {}", username);
        return gerarResposta(usuario);
    }

    private LoginResponseDTO gerarResposta(Usuario usuario) {
        return new LoginResponseDTO(
                jwtService.gerarToken(usuario.getUsername(), usuario.getRole()),
                "Bearer",
                jwtService.getExpiracaoSegundos(),
                usuario.getUsername(),
                usuario.getRole(),
                Boolean.TRUE.equals(usuario.getTrocarSenha())
        );
    }

    // evita log injection (quebra de linha forjando entradas no log)
    private static String sanitizar(String valor) {
        return valor == null ? null : valor.replaceAll("[\\r\\n\\t]", "_");
    }
}
