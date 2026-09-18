package com.AJTBackend.controller;

import com.AJTBackend.dto.OrdemServicoRequestDTO;
import com.AJTBackend.dto.OrdemServicoResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.service.OrdemServicoService;
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

@Tag(name = "Ordens de serviço", description = "Agrupam transfers de um motorista e um veículo em um dia.")
@RestController
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;

    @Operation(summary = "Lista ordens de serviço (paginado)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<OrdemServicoResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ordemServicoService.listarTodos(pageable));
    }

    @Operation(summary = "Busca ordem de serviço por ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ordemServicoService.buscarPorId(id));
    }

    @Operation(summary = "Busca ordens de serviço por status")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Página devolvida (pode vir vazia)")})
    @GetMapping("/buscar")
    public ResponseEntity<PaginaResponseDTO<OrdemServicoResponseDTO>> buscarPorStatus(@RequestParam StatusOrdemServico status,
                                                                    @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ordemServicoService.buscarPorStatus(status, pageable));
    }

    @Operation(summary = "Cria ordem de serviço")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Criado"), @ApiResponse(responseCode = "400", description = "Dados inválidos ou registro duplicado"), @ApiResponse(responseCode = "404", description = "Recurso relacionado não encontrado")})
    @PostMapping
    public ResponseEntity<OrdemServicoResponseDTO> criar(@Valid @RequestBody OrdemServicoRequestDTO dto) {
        OrdemServicoResponseDTO criado = ordemServicoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @Operation(summary = "Atualiza ordem de serviço (substituição total)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PutMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> atualizar(@PathVariable Long id,
                                                              @Valid @RequestBody OrdemServicoRequestDTO dto) {
        return ResponseEntity.ok(ordemServicoService.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza ordem de serviço parcialmente (só os campos enviados)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PatchMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                     @Validated(OnPatch.class) @RequestBody OrdemServicoRequestDTO dto) {
        return ResponseEntity.ok(ordemServicoService.atualizarParcial(id, dto));
    }

    @Operation(summary = "Exclui ordem de serviço")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Excluído"), @ApiResponse(responseCode = "404", description = "Não encontrado"), @ApiResponse(responseCode = "409", description = "Registro em uso por outro recurso")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        ordemServicoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
