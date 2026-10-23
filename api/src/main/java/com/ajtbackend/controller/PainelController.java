package com.ajtbackend.controller;

import com.ajtbackend.dto.PainelResumoDTO;
import com.ajtbackend.service.PainelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Painel", description = "Indicadores operacionais do dia (HU08, RF012).")
@RestController
@RequestMapping("/api/painel")
@RequiredArgsConstructor
public class PainelController {

    private final PainelService painelService;

    @Operation(summary = "Resumo do dia: transfers e ordens de serviço por status, e faturamento",
            description = "Sem ?data usa o dia de hoje (fuso de São Paulo). "
                    + "O faturamento só vem preenchido para ADMIN e GERENTE; para os outros perfis vem null.")
    @ApiResponse(responseCode = "200", description = "Resumo devolvido")
    @ApiResponse(responseCode = "400", description = "Data inválida (use AAAA-MM-DD)")
    @GetMapping("/resumo")
    public ResponseEntity<PainelResumoDTO> resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(painelService.resumo(data));
    }
}
