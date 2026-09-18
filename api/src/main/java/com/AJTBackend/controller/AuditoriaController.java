package com.AJTBackend.controller;

import com.AJTBackend.dto.AuditoriaResponseDTO;
import com.AJTBackend.dto.PaginaResponseDTO;
import com.AJTBackend.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auditoria", description = "Trilha de quem alterou transfers e ordens de serviço. Somente leitura; ADMIN e GERENTE.")
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @Operation(summary = "Lista a trilha de auditoria (paginado, mais recente primeiro)",
            description = "Filtre por recurso com ?tabela=transfers ou ?tabela=ordens_servico.")
    // ?tabela=transfers filtra por recurso; sem filtro devolve tudo, do mais recente pro mais antigo
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<AuditoriaResponseDTO>> listar(
            @RequestParam(required = false) String tabela,
            @ParameterObject @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditoriaService.listar(tabela, pageable));
    }
}
