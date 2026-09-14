package com.AJTBackend.controller;

import com.AJTBackend.dto.TransferRequestDTO;
import com.AJTBackend.dto.TransferResponseDTO;
import com.AJTBackend.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @GetMapping
    public ResponseEntity<List<TransferResponseDTO>> listarTodos() {
        return ResponseEntity.ok(transferService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<TransferResponseDTO>> buscarPorStatus(@RequestParam String status) {
        return ResponseEntity.ok(transferService.buscarPorStatus(status));
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
                                                                 @RequestBody TransferRequestDTO dto) {
        return ResponseEntity.ok(transferService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        transferService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
