package com.AJTBackend.controller;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.PontoColetaRequestDTO;
import com.AJTBackend.dto.PontoColetaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.PontoColetaService;
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

import java.util.List;

@Tag(name = "Pontos de coleta", description = "Pontos de embarque ordenados dentro de um transfer.")
@RestController
@RequestMapping("/api/pontos-coleta")
@RequiredArgsConstructor
public class PontoColetaController {

    private final PontoColetaService pontoColetaService;

    @Operation(summary = "Lista pontos de coleta (paginado)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<PontoColetaResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(pontoColetaService.listarTodos(pageable));
    }

    @Operation(summary = "Busca ponto de coleta por ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}")
    public ResponseEntity<PontoColetaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pontoColetaService.buscarPorId(id));
    }

    @Operation(summary = "Lista os pontos de coleta de um transfer")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping("/transfer/{transferId}")
    public ResponseEntity<List<PontoColetaResponseDTO>> listarPorTransfer(@PathVariable Long transferId) {
        return ResponseEntity.ok(pontoColetaService.listarPorTransfer(transferId));
    }

    @Operation(summary = "Cria ponto de coleta")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Criado"), @ApiResponse(responseCode = "400", description = "Dados inválidos ou registro duplicado"), @ApiResponse(responseCode = "404", description = "Recurso relacionado não encontrado")})
    @PostMapping
    public ResponseEntity<PontoColetaResponseDTO> criar(@Valid @RequestBody PontoColetaRequestDTO dto) {
        PontoColetaResponseDTO criado = pontoColetaService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @Operation(summary = "Atualiza ponto de coleta (substituição total)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PutMapping("/{id}")
    public ResponseEntity<PontoColetaResponseDTO> atualizar(@PathVariable Long id,
                                                             @Valid @RequestBody PontoColetaRequestDTO dto) {
        return ResponseEntity.ok(pontoColetaService.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza ponto de coleta parcialmente (só os campos enviados)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PatchMapping("/{id}")
    public ResponseEntity<PontoColetaResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                    @Validated(OnPatch.class) @RequestBody PontoColetaRequestDTO dto) {
        return ResponseEntity.ok(pontoColetaService.atualizarParcial(id, dto));
    }

    @Operation(summary = "Exclui ponto de coleta")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Excluído"), @ApiResponse(responseCode = "404", description = "Não encontrado"), @ApiResponse(responseCode = "409", description = "Registro em uso por outro recurso")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        pontoColetaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
