package com.AJTBackend.controller;

import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.StatusTransfer;
import com.AJTBackend.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<TransferResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(transferService.listarTodos(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<PaginaResponseDTO<TransferResponseDTO>> buscarPorStatus(@RequestParam StatusTransfer status,
                                                                    @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(transferService.buscarPorStatus(status, pageable));
    }

    @PostMapping
    public ResponseEntity<TransferResponseDTO> criar(@Valid @RequestBody TransferRequestDTO dto) {
        TransferResponseDTO criado = transferService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> atualizar(@PathVariable Long id,
                                                          @Valid @RequestBody TransferRequestDTO dto) {
        return ResponseEntity.ok(transferService.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                 @Validated(OnPatch.class) @RequestBody TransferRequestDTO dto) {
        return ResponseEntity.ok(transferService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        transferService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
