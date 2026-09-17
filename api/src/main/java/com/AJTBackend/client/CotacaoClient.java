package com.AJTBackend.client;

import com.AJTBackend.dto.CotacaoResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client Feign para a Frankfurter API (cambio do Banco Central Europeu,
 * publica e sem necessidade de chave). https://frankfurter.dev
 */
@FeignClient(name = "cotacaoClient", url = "${ajt.cotacao.url}")
public interface CotacaoClient {

    @GetMapping("/latest")
    CotacaoResponseDTO obterCotacao(@RequestParam("base") String moedaOrigem,
                                     @RequestParam("symbols") String moedaDestino);
}
