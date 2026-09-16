package com.AJTBackend.controller;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.service.CotacaoService;
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
    public ResponseEntity<CotacaoDTO> obterCotacao(@RequestParam String de, @RequestParam String para) {
        return ResponseEntity.ok(cotacaoService.obterCotacao(de, para));
    }
}
