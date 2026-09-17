package com.AJTBackend.controller;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.service.CotacaoService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cotacao")
@RequiredArgsConstructor
public class CotacaoController {

    private final CotacaoService cotacaoService;

    @GetMapping
    public ResponseEntity<CotacaoDTO> obterCotacao(
            @RequestParam @Pattern(regexp = "^[A-Za-z]{3}$", message = "de: código de moeda ISO de 3 letras (ex: USD)") String de,
            @RequestParam @Pattern(regexp = "^[A-Za-z]{3}$", message = "para: código de moeda ISO de 3 letras (ex: BRL)") String para) {
        return ResponseEntity.ok(cotacaoService.obterCotacao(de, para));
    }
}
