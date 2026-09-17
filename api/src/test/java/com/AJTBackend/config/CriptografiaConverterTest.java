package com.AJTBackend.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * o que testa: o conversor jpa que cifra/decifra o documento do passageiro
 * com AES-256-GCM (CriptografiaConverter) — ida e volta, valores repetidos
 * gerando cifras diferentes, dado adulterado, chave errada e chave invalida.
 *
 * como rodar: teste unitario puro (sem spring, sem banco, sem docker).
 * roda com "mvn test" normalmente, em qualquer maquina.
 *
 * por que existe: e o unico ponto do sistema que protege o documento do
 * passageiro (lgpd). um bug de criptografia aqui e silencioso — o app
 * continua funcionando, mas o dado sai exposto ou vira ilegivel no banco.
 * por isso cobrimos os casos de borda (chave curta, texto adulterado, valor
 * legado sem prefixo) e nao so o caminho feliz.
 */
class CriptografiaConverterTest {

    private static final String CHAVE = base64("0123456789abcdef0123456789abcdef");

    private final CriptografiaConverter converter = new CriptografiaConverter(CHAVE);

    @Test
    void cifraEDecifraMantendoOValor() {
        String cifrado = converter.convertToDatabaseColumn("AB123456");

        assertThat(cifrado).startsWith("v1:").doesNotContain("AB123456");
        assertThat(converter.convertToEntityAttribute(cifrado)).isEqualTo("AB123456");
    }

    @Test
    void mesmoValorGeraCifrasDiferentes() {
        // IV aleatorio: impede descobrir documentos iguais comparando o banco
        assertThat(converter.convertToDatabaseColumn("123"))
                .isNotEqualTo(converter.convertToDatabaseColumn("123"));
    }

    @Test
    void preservaCaracteresEspeciais() {
        String valor = "Passaporte nº ÇÃÕ-ß-日本";
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(valor))).isEqualTo(valor);
    }

    @Test
    void nuloContinuaNulo() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void valorLegadoSemPrefixoEhLidoComoTextoPuro() {
        assertThat(converter.convertToEntityAttribute("12345678900")).isEqualTo("12345678900");
    }

    @Test
    void falhaAoDecifrarComChaveDiferente() {
        String cifrado = converter.convertToDatabaseColumn("AB123456");
        CriptografiaConverter outraChave = new CriptografiaConverter(base64("fedcba9876543210fedcba9876543210"));

        assertThatThrownBy(() -> outraChave.convertToEntityAttribute(cifrado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void falhaAoDecifrarDadoAdulterado() {
        String cifrado = converter.convertToDatabaseColumn("AB123456");
        String adulterado = cifrado.substring(0, cifrado.length() - 4) + "AAAA";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(adulterado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recusaChaveComTamanhoErrado() {
        assertThatThrownBy(() -> new CriptografiaConverter(base64("curta")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void recusaChaveQueNaoEhBase64() {
        assertThatThrownBy(() -> new CriptografiaConverter("###"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    private static String base64(String valor) {
        return Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }
}
