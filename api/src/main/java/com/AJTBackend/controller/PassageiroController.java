package com.AJTBackend.controller;

import com.AJTBackend.dto.PassageiroRequestDTO;
import com.AJTBackend.dto.PassageiroResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.PassageiroService;
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
@RequestMapping("/api/passageiros")
@RequiredArgsConstructor
public class PassageiroController {

    private final PassageiroService service;

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<PassageiroResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.listarTodos(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<PaginaResponseDTO<PassageiroResponseDTO>> buscarPorNacionalidade(
            @RequestParam String nacionalidade,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.buscarPorNacionalidade(nacionalidade, pageable));
    }

    @PostMapping
    public ResponseEntity<PassageiroResponseDTO> criar(
            @Valid @RequestBody PassageiroRequestDTO dto) {
        PassageiroResponseDTO criado = service.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody PassageiroRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PassageiroResponseDTO> atualizarParcial(
            @PathVariable Long id,
            @Validated(OnPatch.class) @RequestBody PassageiroRequestDTO dto) {
        return ResponseEntity.ok(service.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
