package com.AJTBackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Impressao digital do documento do passageiro, pra achar duplicados sem decifrar nada.
 * HMAC-SHA256 (chave = AJT_CRYPTO_KEY) de "TIPO:NUMERO", com o numero normalizado:
 * "529.982.247-25" e "52998224725" dao o mesmo hash. Sem a chave nao da pra montar
 * uma tabela de CPFs -> hash, entao o hash nao vaza o documento.
 */
@Component
public class DocumentoHash {

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    public DocumentoHash(@Value("${ajt.crypto.key}") String chaveBase64) {
        this.chave = new SecretKeySpec(Base64.getDecoder().decode(chaveBase64), ALGORITMO);
    }

    public String calcular(String tipo, String documento) {
        String normalizado = normalizar(tipo) + ":" + normalizar(documento);
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            return HexFormat.of().formatHex(mac.doFinal(normalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular o hash do documento", e);
        }
    }

    // so letras e numeros, em maiusculas: pontuacao, espacos e caixa nao mudam o documento
    static String normalizar(String valor) {
        return valor == null ? "" : valor.replaceAll("[^\\p{Alnum}]", "").toUpperCase(Locale.ROOT);
    }
}
