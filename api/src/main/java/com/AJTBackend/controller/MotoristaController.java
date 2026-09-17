package com.AJTBackend.controller;

import com.AJTBackend.dto.MotoristaRequestDTO;
import com.AJTBackend.dto.MotoristaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.MotoristaService;
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
@RequestMapping("/api/motoristas")
@RequiredArgsConstructor
public class MotoristaController {

    private final MotoristaService motoristaService;

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<MotoristaResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(motoristaService.listarTodos(pageable));
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
                                                                 @Validated(OnPatch.class) @RequestBody MotoristaRequestDTO dto) {
        return ResponseEntity.ok(motoristaService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        motoristaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}