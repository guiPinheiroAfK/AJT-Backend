package com.AJTBackend.controller;

import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.service.CotacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cotação", description = "Consulta de câmbio via Spring Cloud OpenFeign (Frankfurter API, Banco Central Europeu).")
@RestController
@RequestMapping("/api/cotacao")
@RequiredArgsConstructor
public class CotacaoController {

    private final CotacaoService cotacaoService;

    @Operation(summary = "Cotação entre duas moedas",
            description = "Chama a API externa via Feign. O resultado fica em cache por 30 minutos por par de moedas.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Cotação devolvida"), @ApiResponse(responseCode = "400", description = "Código de moeda inválido (use 3 letras, ex: USD)"), @ApiResponse(responseCode = "502", description = "API de câmbio indisponível")})
    @GetMapping
    public ResponseEntity<CotacaoDTO> obterCotacao(
            @RequestParam @Pattern(regexp = "^[A-Za-z]{3}$", message = "de: código de moeda ISO de 3 letras (ex: USD)") String de,
            @RequestParam @Pattern(regexp = "^[A-Za-z]{3}$", message = "para: código de moeda ISO de 3 letras (ex: BRL)") String para) {
        return ResponseEntity.ok(cotacaoService.obterCotacao(de, para));
    }
}
