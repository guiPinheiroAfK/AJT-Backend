package com.AJTBackend.controller;

import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.service.PassageiroService;
import com.AJTBackend.service.TransferService;
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

@Tag(name = "Transfers", description = "Deslocamentos agendados. Em moeda estrangeira, o valor em reais é convertido pela API externa de câmbio (Feign).")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final PassageiroService passageiroService;

    @Operation(summary = "Lista transfers (paginado)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista devolvida")})
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<TransferResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(transferService.listarTodos(pageable));
    }

    @Operation(summary = "Busca transfer por ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.buscarPorId(id));
    }

    // passageiros do transfer com os dados completos (o transfer traz so os ids)
    @Operation(summary = "Lista os passageiros de um transfer", description = "Devolve os dados completos dos passageiros do transfer (o transfer traz só os ids).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrado"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @GetMapping("/{id}/passageiros")
    public ResponseEntity<List<PassageiroResponseDTO>> listarPassageiros(@PathVariable Long id) {
        transferService.buscarPorId(id); // 404 se o transfer nao existe
        return ResponseEntity.ok(passageiroService.listarPorTransfer(id));
    }

    @Operation(summary = "Busca transfers por status")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Página devolvida (pode vir vazia)")})
    @GetMapping("/buscar")
    public ResponseEntity<PaginaResponseDTO<TransferResponseDTO>> buscarPorStatus(@RequestParam StatusTransfer status,
                                                                    @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(transferService.buscarPorStatus(status, pageable));
    }

    @Operation(summary = "Cria transfer", description = "Se valorBase não for informado e a moeda for estrangeira, o backend consulta a cotação (cache de 30 min) e converte para BRL. Se a API de câmbio estiver fora do ar, o transfer é criado com valorBase nulo. passageiroIds vincula os passageiros do transfer.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Criado"), @ApiResponse(responseCode = "400", description = "Dados inválidos ou registro duplicado"), @ApiResponse(responseCode = "404", description = "Recurso relacionado não encontrado")})
    @PostMapping
    public ResponseEntity<TransferResponseDTO> criar(@Valid @RequestBody TransferRequestDTO dto) {
        TransferResponseDTO criado = transferService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @Operation(summary = "Atualiza transfer (substituição total)", description = "PUT substitui o registro. passageiroIds ausente mantém os passageiros atuais; [] remove todos.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PutMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> atualizar(@PathVariable Long id,
                                                          @Valid @RequestBody TransferRequestDTO dto) {
        return ResponseEntity.ok(transferService.atualizar(id, dto));
    }

    @Operation(summary = "Atualiza transfer parcialmente (só os campos enviados)", description = "Altera só os campos enviados. Mudança de status fica registrada na auditoria.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Atualizado"), @ApiResponse(responseCode = "400", description = "Dados inválidos"), @ApiResponse(responseCode = "404", description = "Não encontrado")})
    @PatchMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                 @Validated(OnPatch.class) @RequestBody TransferRequestDTO dto) {
        return ResponseEntity.ok(transferService.atualizarParcial(id, dto));
    }

    @Operation(summary = "Exclui transfer")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Excluído"), @ApiResponse(responseCode = "404", description = "Não encontrado"), @ApiResponse(responseCode = "409", description = "Registro em uso por outro recurso")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        transferService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
