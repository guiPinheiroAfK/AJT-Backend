package com.AJTBackend.controller;

import com.AJTBackend.dto.LoginRequestDTO;
import com.AJTBackend.dto.LoginResponseDTO;
import com.AJTBackend.config.JwtService;
import com.AJTBackend.exception.CredenciaisInvalidasException;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByUsername(dto.username())
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(dto.senha(), usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new CredenciaisInvalidasException(); // ou uma exception própria de "usuário inativo", se preferir diferenciar
        }

        String token = jwtService.gerarToken(usuario.getUsername(), usuario.getRole());

        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                usuario.getUsername(),
                usuario.getRole()
        ));
    }
}