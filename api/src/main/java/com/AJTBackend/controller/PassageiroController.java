package com.AJTBackend.controller;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.PassageiroService;
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

@Tag(name = "Passageiros", description = "Cadastro de passageiros. O documento é cifrado (AES-256-GCM) no banco e volta legível na API.")
@RestController
@RequestMapping("/api/passageiros")
@RequiredArgsConstructor
public class PassageiroController {

    private final PassageiroService service;

    @Operation(summary = "Lista passageiros (paginado)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<PassageiroResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.listarTodos(pageable));
    }

    @Operation(summary = "Busca passageiro por ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Busca passageiros por nacionalidade")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Página devolvida (pode vir vazia)")})
    @GetMapping("/buscar")
    public ResponseEntity<PaginaResponseDTO<PassageiroResponseDTO>> buscarPorNacionalidade(
            @RequestParam String nacionalidade,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.buscarPorNacionalidade(nacionalidade, pageable));
    }

    @Operation(summary = "Cria passageiro")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Criado"), @ApiResponse(responseCode = "400", description = "Dados inválidos ou registro duplicado"), @ApiResponse(responseCode = "404", description = "Recurso relacionado não encontrado")})
    @PostMapping
    public ResponseEntity<PassageiroResponseDTO> criar(
            @Valid @RequestBody PassageiroRequestDTO dto) {
        PassageiroResponseDTO criado = service.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @Operation(summary = "Atualiza passageiro (substituição total)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PutMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody PassageiroRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza passageiro parcialmente (só os campos enviados)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PatchMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> atualizarParcial(
            @PathVariable Long id,
            @Validated(OnPatch.class) @RequestBody PassageiroRequestDTO dto) {
        return ResponseEntity.ok(service.atualizarParcial(id, dto));
    }

    @Operation(summary = "Exclui passageiro")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Excluído"), @ApiResponse(responseCode = "404", description = "Não encontrado"), @ApiResponse(responseCode = "409", description = "Registro em uso por outro recurso")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
