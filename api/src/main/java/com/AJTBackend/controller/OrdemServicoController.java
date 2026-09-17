package com.AJTBackend.controller;

import com.AJTBackend.dto.OrdemServicoRequestDTO;
import com.AJTBackend.dto.OrdemServicoResponseDTO;
import com.AJTBackend.service.OrdemServicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;

    @GetMapping
    public ResponseEntity<List<OrdemServicoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(ordemServicoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ordemServicoService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<OrdemServicoResponseDTO>> buscarPorStatus(@RequestParam String status) {
        return ResponseEntity.ok(ordemServicoService.buscarPorStatus(status));
    }

    @PostMapping
    public ResponseEntity<OrdemServicoResponseDTO> criar(@Valid @RequestBody OrdemServicoRequestDTO dto) {
        OrdemServicoResponseDTO criado = ordemServicoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> atualizar(@PathVariable Long id,
                                                              @Valid @RequestBody OrdemServicoRequestDTO dto) {
        return ResponseEntity.ok(ordemServicoService.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                     @RequestBody OrdemServicoRequestDTO dto) {
        return ResponseEntity.ok(ordemServicoService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        ordemServicoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
