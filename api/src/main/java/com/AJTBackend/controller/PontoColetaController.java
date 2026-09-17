package com.AJTBackend.controller;

import com.AJTBackend.dto.PontoColetaRequestDTO;
import com.AJTBackend.dto.PontoColetaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.service.PontoColetaService;
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

@RestController
@RequestMapping("/api/pontos-coleta")
@RequiredArgsConstructor
public class PontoColetaController {

    private final PontoColetaService pontoColetaService;

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<PontoColetaResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(pontoColetaService.listarTodos(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PontoColetaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pontoColetaService.buscarPorId(id));
    }

    @GetMapping("/transfer/{transferId}")
    public ResponseEntity<List<PontoColetaResponseDTO>> listarPorTransfer(@PathVariable Long transferId) {
        return ResponseEntity.ok(pontoColetaService.listarPorTransfer(transferId));
    }

    @PostMapping
    public ResponseEntity<PontoColetaResponseDTO> criar(@Valid @RequestBody PontoColetaRequestDTO dto) {
        PontoColetaResponseDTO criado = pontoColetaService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PontoColetaResponseDTO> atualizar(@PathVariable Long id,
                                                             @Valid @RequestBody PontoColetaRequestDTO dto) {
        return ResponseEntity.ok(pontoColetaService.atualizar(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PontoColetaResponseDTO> atualizarParcial(@PathVariable Long id,
                                                                    @Validated(OnPatch.class) @RequestBody PontoColetaRequestDTO dto) {
        return ResponseEntity.ok(pontoColetaService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        pontoColetaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
