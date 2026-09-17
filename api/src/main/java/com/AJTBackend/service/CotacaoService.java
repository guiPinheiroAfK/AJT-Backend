package com.AJTBackend.service;

import com.AJTBackend.client.CotacaoClient;
import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.CotacaoResponseDTO;
import com.AJTBackend.exception.CotacaoIndisponivelException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CotacaoService {

    private static final Logger log = LoggerFactory.getLogger(CotacaoService.class);

    private final CotacaoClient cotacaoClient;

    /**
     * cotacao mais recente, em cache por par de moedas (ttl em
     * spring.cache.caffeine.spec). Excecoes nao entram no cache.
     */
    @Cacheable(cacheNames = "cotacoes", key = "#moedaOrigem.toUpperCase() + '-' + #moedaDestino.toUpperCase()")
    public CotacaoDTO obterCotacao(String moedaOrigem, String moedaDestino) {
        String origem = moedaOrigem.toUpperCase(Locale.ROOT);
        String destino = moedaDestino.toUpperCase(Locale.ROOT);

        if (origem.equals(destino)) {
            return new CotacaoDTO(origem, destino, BigDecimal.ONE, null);
        }

        try {
            CotacaoResponseDTO resposta = cotacaoClient.obterCotacao(origem, destino);
            BigDecimal taxa = resposta.rates() == null ? null : resposta.rates().get(destino);
            if (taxa == null) {
                throw new IllegalStateException("Moeda de destino nao retornada pela API: " + destino);
            }
            log.info("Cotacao obtida da API externa: {} -> {} = {}", origem, destino, taxa);
            return new CotacaoDTO(origem, destino, taxa, resposta.date());
        } catch (Exception e) {
            throw new CotacaoIndisponivelException(origem, destino, e);
        }
    }
}
