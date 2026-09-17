package com.AJTBackend.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra campos sensiveis (ex: documento do passageiro) com AES-256-GCM.
 * Formato gravado: "v1:" + Base64(IV de 12 bytes + ciphertext + tag).
 * Valores sem o prefixo sao tratados como legado em texto puro e lidos
 * como estao — sao cifrados na proxima vez que o registro for salvo.
 */
@Component
@Converter
public class CriptografiaConverter implements AttributeConverter<String, String> {

    private static final String PREFIXO = "v1:";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final SecretKey chave;
    private final SecureRandom random = new SecureRandom();

    public CriptografiaConverter(@Value("${ajt.crypto.key}") String chaveBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(chaveBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ajt.crypto.key (AJT_CRYPTO_KEY) deve estar em Base64", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("ajt.crypto.key (AJT_CRYPTO_KEY) deve ter 32 bytes (AES-256). Gere com: openssl rand -base64 32");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANHO_IV];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));

            byte[] saida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIXO + Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar campo sensivel", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String valorBanco) {
        if (valorBanco == null || !valorBanco.startsWith(PREFIXO)) {
            return valorBanco;
        }
        try {
            byte[] entrada = Base64.getDecoder().decode(valorBanco.substring(PREFIXO.length()));

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, entrada, 0, TAMANHO_IV));
            byte[] claro = cipher.doFinal(entrada, TAMANHO_IV, entrada.length - TAMANHO_IV);

            return new String(claro, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao decifrar campo sensivel (chave AJT_CRYPTO_KEY diferente da usada na gravacao?)", e);
        }
    }
}
