package com.AJTBackend.controller;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.UsuarioRequestDTO;
import com.AJTBackend.dto.UsuarioResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Usuários", description = "Gestão de usuários e perfis. Somente ADMIN.")
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Lista usuários (paginado)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<UsuarioResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listarTodos(pageable));
    }

    @Operation(summary = "Busca usuário por ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @Operation(summary = "Busca usuário por username")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/buscar")
    public ResponseEntity<UsuarioResponseDTO> buscarPorUsername(@RequestParam String username) {
        return ResponseEntity.ok(usuarioService.buscarPorUsername(username));
    }

    @Operation(summary = "Cria usuário", description = "A senha é gravada como hash BCrypt. O usuário criado precisa trocar a senha no primeiro acesso.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Criado"), @ApiResponse(responseCode = "400", description = "Dados inválidos ou registro duplicado"), @ApiResponse(responseCode = "404", description = "Recurso relacionado não encontrado")})
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody UsuarioRequestDTO dto) {
        UsuarioResponseDTO criado = usuarioService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @Operation(summary = "Atualiza usuário (substituição total)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizar(@PathVariable Long id,
                                                        @Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza usuário parcialmente (só os campos enviados)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizarParcial(@PathVariable Long id,
                                                               @Validated(OnPatch.class) @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizarParcial(id, dto));
    }

    @Operation(summary = "Exclui usuário", description = "Ninguém pode excluir o próprio usuário.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Excluído"), @ApiResponse(responseCode = "404", description = "Não encontrado"), @ApiResponse(responseCode = "409", description = "Registro em uso por outro recurso")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        usuarioService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}