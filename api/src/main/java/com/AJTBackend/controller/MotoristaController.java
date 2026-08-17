package com.AJTBackend.controller;

import com.AJTBackend.dto.MotoristaRequestDTO;
import com.AJTBackend.dto.MotoristaResponseDTO;
import com.AJTBackend.service.MotoristaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/motoristas")
@RequiredArgsConstructor
public class MotoristaController {

    private final MotoristaService motoristaService;

    @GetMapping
    public ResponseEntity<List<MotoristaResponseDTO>> listarTodos() {
        return ResponseEntity.ok(motoristaService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MotoristaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(motoristaService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<MotoristaResponseDTO> buscarPorCnh(@RequestParam String cnh) {
        return ResponseEntity.ok(motoristaService.buscarPorCnh(cnh));
    }

    @PostMapping
    public ResponseEntity<MotoristaResponseDTO> criar(@Valid @RequestBody MotoristaRequestDTO dto) {
        MotoristaResponseDTO criado = motoristaService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MotoristaResponseDTO> atualizar(@PathVariable Long id,
                                                          @Valid @RequestBody MotoristaRequestDTO dto) {
        return ResponseEntity.ok(motoristaService.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MotoristaResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                 @RequestBody MotoristaRequestDTO dto) {
        return ResponseEntity.ok(motoristaService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        motoristaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}