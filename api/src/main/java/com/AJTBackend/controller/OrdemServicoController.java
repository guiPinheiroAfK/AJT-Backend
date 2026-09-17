package com.AJTBackend.controller;

import com.AJTBackend.dto.OrdemServicoRequestDTO;
import com.AJTBackend.dto.OrdemServicoResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.dto.validacao.OnPatch;
import com.AJTBackend.model.enums.StatusOrdemServico;
import com.AJTBackend.service.OrdemServicoService;
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
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<OrdemServicoResponseDTO>> listarTodos(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ordemServicoService.listarTodos(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ordemServicoService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<PaginaResponseDTO<OrdemServicoResponseDTO>> buscarPorStatus(@RequestParam StatusOrdemServico status,
                                                                    @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ordemServicoService.buscarPorStatus(status, pageable));
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
                                                                     @Validated(OnPatch.class) @RequestBody OrdemServicoRequestDTO dto) {
        return ResponseEntity.ok(ordemServicoService.atualizarParcial(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        ordemServicoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
