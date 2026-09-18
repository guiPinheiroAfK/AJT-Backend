package com.AJTBackend.controller;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.ParadaOsRequestDTO;
import com.AJTBackend.dto.ParadaOsResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.ParadaOsService;
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

@Tag(name = "Paradas de OS", description = "Paradas de uma ordem de serviço, agrupando os transfers atendidos em cada ponto.")
@RestController
@RequestMapping("/api/paradas-os")
@RequiredArgsConstructor
public class ParadaOsController {

    private final ParadaOsService paradaOsService;

    @Operation(summary = "Lista paradas (paginado)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<ParadaOsResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(paradaOsService.listarTodos(pageable));
    }

    @Operation(summary = "Busca parada por ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}")
    public ResponseEntity<ParadaOsResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(paradaOsService.buscarPorId(id));
    }

    @Operation(summary = "Lista as paradas de uma ordem de serviço")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping("/ordem-servico/{osId}")
    public ResponseEntity<List<ParadaOsResponseDTO>> listarPorOrdemServico(@PathVariable Long osId) {
        return ResponseEntity.ok(paradaOsService.listarPorOrdemServico(osId));
    }

    @Operation(summary = "Cria parada", description = "transferIds vincula os transfers atendidos nesta parada (relação N:N).")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Criado"), @ApiResponse(responseCode = "400", description = "Dados inválidos ou registro duplicado"), @ApiResponse(responseCode = "404", description = "Recurso relacionado não encontrado")})
    @PostMapping
    public ResponseEntity<ParadaOsResponseDTO> criar(@Valid @RequestBody ParadaOsRequestDTO dto) {
        ParadaOsResponseDTO criado = paradaOsService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @Operation(summary = "Atualiza parada (substituição total)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PutMapping("/{id}")
    public ResponseEntity<ParadaOsResponseDTO> atualizar(@PathVariable Long id,
                                                          @Valid @RequestBody ParadaOsRequestDTO dto) {
        return ResponseEntity.ok(paradaOsService.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza parada parcialmente (só os campos enviados)", description = "O perfil MOTORISTA só pode alterar statusParada; qualquer outro campo devolve 403.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PatchMapping("/{id}")
    public ResponseEntity<ParadaOsResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                 @Validated(OnPatch.class) @RequestBody ParadaOsRequestDTO dto) {
        return ResponseEntity.ok(paradaOsService.atualizarParcial(id, dto));
    }

    @Operation(summary = "Exclui parada")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Excluído"), @ApiResponse(responseCode = "404", description = "Não encontrado"), @ApiResponse(responseCode = "409", description = "Registro em uso por outro recurso")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        paradaOsService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
