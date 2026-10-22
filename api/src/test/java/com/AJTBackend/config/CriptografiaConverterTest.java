package com.AJTBackend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriptografiaConverterTest {

    private static final String CHAVE = base64("0123456789abcdef0123456789abcdef");
    private static final String DOCUMENTO = "AB123456";

    private final CriptografiaConverter converter = new CriptografiaConverter(CHAVE);

    // ----- cifra e decifra -----

    @Test
    void valorCifradoTemOPrefixoDaVersaoENaoExibeOTextoOriginal() {
        // When
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);

        // Then
        assertThat(cifrado).startsWith("v1:").doesNotContain(DOCUMENTO);
    }

    @Test
    void decifrarOValorCifradoDevolveOOriginal() {
        // Given
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);

        // When
        String decifrado = converter.convertToEntityAttribute(cifrado);

        // Then
        assertThat(decifrado).isEqualTo(DOCUMENTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "12345678900", "  com espacos  ", "Passaporte nº ÇÃÕ-ß-日本"})
    void idaEVoltaPreservaQualquerTexto(String valor) {
        // Given
        String cifrado = converter.convertToDatabaseColumn(valor);

        // When
        String decifrado = converter.convertToEntityAttribute(cifrado);

        // Then
        assertThat(decifrado).isEqualTo(valor);
    }

    @Test
    void idaEVoltaPreservaUmTextoLongo() {
        // Given
        String longo = "a".repeat(1000);
        String cifrado = converter.convertToDatabaseColumn(longo);

        // When
        String decifrado = converter.convertToEntityAttribute(cifrado);

        // Then
        assertThat(decifrado).isEqualTo(longo).hasSize(1000);
    }

    @Test
    void mesmoValorGeraCifrasDiferentes() {
        // Given: IV aleatorio impede descobrir documentos iguais comparando o banco

        // When
        String primeira = converter.convertToDatabaseColumn("123");
        String segunda = converter.convertToDatabaseColumn("123");

        // Then
        assertThat(primeira).isNotEqualTo(segunda);
    }

    @Test
    void cifrasDiferentesDoMesmoValorDeciframParaOMesmoTexto() {
        // Given
        String primeira = converter.convertToDatabaseColumn("123");
        String segunda = converter.convertToDatabaseColumn("123");

        // When
        String decifradaPrimeira = converter.convertToEntityAttribute(primeira);
        String decifradaSegunda = converter.convertToEntityAttribute(segunda);

        // Then
        assertThat(decifradaPrimeira).isEqualTo("123");
        assertThat(decifradaSegunda).isEqualTo("123");
    }

    @Test
    void outraInstanciaComAMesmaChaveDecifraOQueFoiGravado() {
        // Given: simula o servidor reiniciando e lendo o que ja estava no banco
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);
        CriptografiaConverter aposReinicio = new CriptografiaConverter(CHAVE);

        // When
        String decifrado = aposReinicio.convertToEntityAttribute(cifrado);

        // Then
        assertThat(decifrado).isEqualTo(DOCUMENTO);
    }

    // ----- nulo e valor legado -----

    @Test
    void cifrarNuloDevolveNulo() {
        // When
        String cifrado = converter.convertToDatabaseColumn(null);

        // Then
        assertThat(cifrado).isNull();
    }

    @Test
    void decifrarNuloDevolveNulo() {
        // When
        String decifrado = converter.convertToEntityAttribute(null);

        // Then
        assertThat(decifrado).isNull();
    }

    @Test
    void valorLegadoSemPrefixoEhLidoComoTextoPuro() {
        // When
        String lido = converter.convertToEntityAttribute("12345678900");

        // Then
        assertThat(lido).isEqualTo("12345678900");
    }

    // ----- falhas ao decifrar -----

    @Test
    void falhaAoDecifrarComChaveDiferente() {
        // Given
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);
        CriptografiaConverter outraChave = new CriptografiaConverter(base64("fedcba9876543210fedcba9876543210"));

        // When / Then
        assertThatThrownBy(() -> outraChave.convertToEntityAttribute(cifrado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(DOCUMENTO);
    }

    @Test
    void falhaAoDecifrarDadoAdulterado() {
        // Given: troca os ultimos 4 caracteres do valor gravado
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);
        String adulterado = cifrado.substring(0, cifrado.length() - 4) + "AAAA";

        // When / Then
        assertThatThrownBy(() -> converter.convertToEntityAttribute(adulterado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(DOCUMENTO);
    }

    // ----- chave invalida -----

    @Test
    void recusaChaveComTamanhoErrado() {
        // Given
        String chaveCurta = base64("curta");

        // When / Then
        assertThatThrownBy(() -> new CriptografiaConverter(chaveCurta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaChaveQueNaoEhBase64() {
        // When / Then
        assertThatThrownBy(() -> new CriptografiaConverter("###"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    private static String base64(String valor) {
        return Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }
}