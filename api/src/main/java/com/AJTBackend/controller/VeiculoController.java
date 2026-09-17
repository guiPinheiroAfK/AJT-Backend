package com.AJTBackend.controller;

import com.AJTBackend.dto.VeiculoRequestDTO;
import com.AJTBackend.dto.VeiculoResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.VeiculoService;
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
@RequestMapping("/api/veiculos")
@RequiredArgsConstructor
public class VeiculoController {

    private final VeiculoService veiculoService;

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<VeiculoResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(veiculoService.listarTodos(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VeiculoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(veiculoService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<VeiculoResponseDTO> buscarPorPlaca(@RequestParam String placa) {
        return ResponseEntity.ok(veiculoService.buscarPorPlaca(placa));
    }

    @PostMapping
    public ResponseEntity<VeiculoResponseDTO> criar(@Valid @RequestBody VeiculoRequestDTO dto) {
        VeiculoResponseDTO criado = veiculoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VeiculoResponseDTO> atualizar(@PathVariable Long id,
                                                        @Valid @RequestBody VeiculoRequestDTO dto) {
        return ResponseEntity.ok(veiculoService.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VeiculoResponseDTO> atualizarParcial(@PathVariable Long id,
                                                               @Validated(OnPatch.class) @RequestBody VeiculoRequestDTO dto) {
        return ResponseEntity.ok(veiculoService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        veiculoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}