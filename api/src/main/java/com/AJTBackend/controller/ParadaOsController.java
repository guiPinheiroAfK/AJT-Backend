package com.AJTBackend.controller;

import com.AJTBackend.dto.ParadaOsRequestDTO;
import com.AJTBackend.dto.ParadaOsResponseDTO;
import com.AJTBackend.service.ParadaOsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/paradas-os")
@RequiredArgsConstructor
public class ParadaOsController {

    private final ParadaOsService paradaOsService;

    @GetMapping
    public ResponseEntity<List<ParadaOsResponseDTO>> listarTodos() {
        return ResponseEntity.ok(paradaOsService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParadaOsResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(paradaOsService.buscarPorId(id));
    }

    @GetMapping("/ordem-servico/{osId}")
    public ResponseEntity<List<ParadaOsResponseDTO>> listarPorOrdemServico(@PathVariable Long osId) {
        return ResponseEntity.ok(paradaOsService.listarPorOrdemServico(osId));
    }

    @PostMapping
    public ResponseEntity<ParadaOsResponseDTO> criar(@Valid @RequestBody ParadaOsRequestDTO dto) {
        ParadaOsResponseDTO criado = paradaOsService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParadaOsResponseDTO> atualizar(@PathVariable Long id,
                                                          @Valid @RequestBody ParadaOsRequestDTO dto) {
        return ResponseEntity.ok(paradaOsService.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ParadaOsResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                 @RequestBody ParadaOsRequestDTO dto) {
        return ResponseEntity.ok(paradaOsService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        paradaOsService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
