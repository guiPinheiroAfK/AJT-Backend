package com.AJTBackend.service;

import com.AJTBackend.exception.CotacaoIndisponivelException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * regra de valor do transfer: decide o valorBase (em reais) a partir do que o
 * cliente informou. separada do TransferService pra que ele cuide so de
 * persistencia e a conversao de moeda (com a chamada externa) fique isolada.
 */
@Service
@RequiredArgsConstructor
public class ValorTransferService {

    private static final Logger log = LoggerFactory.getLogger(ValorTransferService.class);
    private static final String MOEDA_PADRAO = "BRL";

    private final CotacaoService cotacaoService;

    /**
     * se valorBase ja foi informado explicitamente, respeita ele. caso
     * contrario, se houver valorOriginal numa moeda estrangeira, converte
     * pra BRL usando a cotacao atual (feign/frankfurter, com cache). se a api
     * de cambio estiver fora do ar devolve null: o cadastro nao trava por
     * causa de uma dependencia externa.
     */
    public BigDecimal calcularValorBase(BigDecimal valorBase, BigDecimal valorOriginal, String moedaOrigem) {
        if (valorBase != null) {
            return valorBase;
        }
        if (valorOriginal == null || moedaOrigem == null || moedaOrigem.equals(MOEDA_PADRAO)) {
            return valorOriginal;
        }
        try {
            BigDecimal taxa = cotacaoService.obterCotacao(moedaOrigem, MOEDA_PADRAO).taxa();
            return valorOriginal.multiply(taxa).setScale(2, RoundingMode.HALF_UP);
        } catch (CotacaoIndisponivelException e) {
            log.warn("Cotacao indisponivel ({} -> {}), valor base nao convertido automaticamente: {}",
                    moedaOrigem, MOEDA_PADRAO, e.getMessage());
            return null;
        }
    }

    /** codigo da moeda sempre em maiusculo (usd -> USD); null continua null. */
    public static String normalizarMoeda(String moeda) {
        return moeda == null ? null : moeda.toUpperCase(Locale.ROOT);
    }
}
