package com.AJTBackend.service;

import com.AJTBackend.client.CotacaoClient;
import com.AJTBackend.dto.CotacaoDTO;
import com.AJTBackend.dto.CotacaoResponseDTO;
import com.AJTBackend.exception.CotacaoIndisponivelException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class CotacaoService {

    private static final Logger log = LoggerFactory.getLogger(CotacaoService.class);

    private final CotacaoClient cotacaoClient;

    public CotacaoDTO obterCotacao(String moedaOrigem, String moedaDestino) {
        BigDecimal taxa = buscarTaxa(moedaOrigem, moedaDestino);
        return new CotacaoDTO(moedaOrigem, moedaDestino, taxa, null);
    }

    /**
     * Converte um valor de uma moeda pra outra usando a cotacao mais recente.
     * Retorna null (em vez de lancar excecao) se o servico externo estiver
     * indisponivel, pra nao travar operacoes internas por causa de uma
     * dependencia externa fora do ar.
     */
    public BigDecimal converterOuNulo(BigDecimal valor, String moedaOrigem, String moedaDestino) {
        try {
            BigDecimal taxa = buscarTaxa(moedaOrigem, moedaDestino);
            return valor.multiply(taxa).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Cotacao indisponivel ({} -> {}), valor nao convertido automaticamente: {}",
                    moedaOrigem, moedaDestino, e.getMessage());
            return null;
        }
    }

    private BigDecimal buscarTaxa(String moedaOrigem, String moedaDestino) {
        if (moedaOrigem.equalsIgnoreCase(moedaDestino)) {
            return BigDecimal.ONE;
        }

        try {
            CotacaoResponseDTO resposta = cotacaoClient.obterCotacao(moedaOrigem, moedaDestino);
            Double taxa = resposta.rates().get(moedaDestino.toUpperCase());
            if (taxa == null) {
                throw new IllegalStateException("Moeda de destino nao retornada pela API: " + moedaDestino);
            }
            return BigDecimal.valueOf(taxa);
        } catch (Exception e) {
            throw new CotacaoIndisponivelException(moedaOrigem, moedaDestino, e);
        }
    }
}
