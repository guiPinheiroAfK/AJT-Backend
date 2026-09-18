package com.AJTBackend.controller;

import com.AJTBackend.dto.LoginRequestDTO;
import com.AJTBackend.dto.LoginResponseDTO;
import com.AJTBackend.dto.TrocaSenhaRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.service.AuthService;
import com.AJTBackend.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Autenticação", description = "Login, sessão atual e troca de senha. O login é a única rota pública.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    @Operation(summary = "Autentica e devolve o token JWT",
            description = "Devolve token, validade em segundos, perfil e trocarSenha (true = usuário precisa definir uma nova senha antes de usar o sistema). Após várias tentativas erradas do mesmo IP/usuário, responde 429 por alguns minutos.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Autenticado"), @ApiResponse(responseCode = "400", description = "Campos ausentes"), @ApiResponse(responseCode = "401", description = "Usuário ou senha inválidos"), @ApiResponse(responseCode = "429", description = "Muitas tentativas de login")})
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto,
                                                  HttpServletRequest request) {
        return ResponseEntity.ok(authService.login(dto, request.getRemoteAddr()));
    }

    // dados do usuario logado (o front usa pra restaurar a sessao ao recarregar a pagina)
    @Operation(summary = "Dados do usuário logado", description = "O front usa para restaurar a sessão ao recarregar a página.")
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> me(Authentication authentication) {
        return ResponseEntity.ok(usuarioService.buscarPorUsername(authentication.getName()));
    }

    @Operation(summary = "Troca a senha do próprio usuário", description = "Devolve um token NOVO: os tokens emitidos antes da troca deixam de valer.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Senha alterada"), @ApiResponse(responseCode = "400", description = "Senha atual incorreta ou nova senha igual à atual")})
    @PutMapping("/senha")
    public ResponseEntity<LoginResponseDTO> trocarSenha(@Valid @RequestBody TrocaSenhaRequestDTO dto,
                                                        Authentication authentication) {
        return ResponseEntity.ok(authService.trocarSenha(authentication.getName(), dto));
    }
}
