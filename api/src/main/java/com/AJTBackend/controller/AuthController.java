package com.AJTBackend.controller;

import com.AJTBackend.dto.LoginRequestDTO;
import com.AJTBackend.dto.LoginResponseDTO;
import com.AJTBackend.config.JwtService;
import com.AJTBackend.exception.CredenciaisInvalidasException;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByUsername(dto.username())
                .orElseThrow(() -> {
                    log.warn("Tentativa de login com username inexistente: {}", dto.username());
                    return new CredenciaisInvalidasException();
                });

        if (!passwordEncoder.matches(dto.senha(), usuario.getSenha())) {
            log.warn("Tentativa de login com senha incorreta para o usuario: {}", dto.username());
            throw new CredenciaisInvalidasException();
        }

        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            log.warn("Tentativa de login em usuario inativo: {}", dto.username());
            throw new CredenciaisInvalidasException(); // ou uma exception própria de "usuário inativo", se preferir diferenciar
        }

        String token = jwtService.gerarToken(usuario.getUsername(), usuario.getRole());
        log.info("Login bem-sucedido: usuario={}, role={}", usuario.getUsername(), usuario.getRole());

        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                usuario.getUsername(),
                usuario.getRole()
        ));
    }
}