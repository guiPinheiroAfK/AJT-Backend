package com.AJTBackend.controller;

import com.AJTBackend.dto.LoginRequestDTO;
import com.AJTBackend.dto.LoginResponseDTO;
import com.AJTBackend.dto.TrocaSenhaRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.service.AuthService;
import com.AJTBackend.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto,
                                                  HttpServletRequest request) {
        return ResponseEntity.ok(authService.login(dto, request.getRemoteAddr()));
    }

    // dados do usuario logado (o front usa pra restaurar a sessao ao recarregar a pagina)
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> me(Authentication authentication) {
        return ResponseEntity.ok(usuarioService.buscarPorUsername(authentication.getName()));
    }

    @PutMapping("/senha")
    public ResponseEntity<LoginResponseDTO> trocarSenha(@Valid @RequestBody TrocaSenhaRequestDTO dto,
                                                        Authentication authentication) {
        return ResponseEntity.ok(authService.trocarSenha(authentication.getName(), dto));
    }
}
