package com.AJTBackend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/*
 * o que testa: a cifra do documento do passageiro (CriptografiaConverter):
 * ida e volta, iv aleatorio, valor legado sem prefixo, nulo, chave errada,
 * dado adulterado e chave invalida na inicializacao.
 *
 * como rodar: teste unitario puro (sem spring, sem banco). "mvn test".
 *
 * por que existe: um erro aqui ou vaza o documento em texto puro (LGPD) ou
 * deixa o dado gravado ilegivel. a gravacao real no postgres fica no
 * PassageiroRepositoryTest; aqui ficam os casos de borda da cifra.
 */
class CriptografiaConverterTest {

    private static final String CHAVE = base64("0123456789abcdef0123456789abcdef");
    private static final String DOCUMENTO = "AB123456";

    private final CriptografiaConverter converter = new CriptografiaConverter(CHAVE);

    // ----- cifra e decifra -----

    @Test
    void deveGravarComPrefixoDeVersaoSemExibirOTexto_quandoCifraUmValor() {
        // when
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);

        // then
        assertThat(cifrado).startsWith("v1:").doesNotContain(DOCUMENTO);
    }

    @Test
    void deveDevolverOOriginal_quandoDecifraOValorCifrado() {
        // given
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);

        // when
        String decifrado = converter.convertToEntityAttribute(cifrado);

        // then
        assertThat(decifrado).isEqualTo(DOCUMENTO);
    }

    @ParameterizedTest(name = "ida e volta preserva \"{0}\"")
    @ValueSource(strings = {"A", "12345678900", "  com espacos  ", "Passaporte nº ÇÃÕ-ß-日本"})
    void devePreservarOTexto_quandoCifraEDecifraQualquerValor(String valor) {
        // given
        String cifrado = converter.convertToDatabaseColumn(valor);

        // when
        String decifrado = converter.convertToEntityAttribute(cifrado);

        // then
        assertThat(decifrado).isEqualTo(valor);
    }

    @Test
    void devePreservarOTexto_quandoValorTemMilCaracteres() {
        // given
        String longo = "a".repeat(1000);
        String cifrado = converter.convertToDatabaseColumn(longo);

        // when
        String decifrado = converter.convertToEntityAttribute(cifrado);

        // then
        assertThat(decifrado).isEqualTo(longo).hasSize(1000);
    }

    @Test
    void deveGerarCifrasDiferentes_quandoCifraOMesmoValorDuasVezes() {
        // given
        // iv aleatorio impede descobrir documentos iguais comparando o banco
        String primeira = converter.convertToDatabaseColumn("123");

        // when
        String segunda = converter.convertToDatabaseColumn("123");

        // then
        assertThat(primeira).isNotEqualTo(segunda);
    }

    @Test
    void deveDecifrarParaOMesmoTexto_quandoCifrasDiferentesSaoDoMesmoValor() {
        // given
        String primeira = converter.convertToDatabaseColumn("123");
        String segunda = converter.convertToDatabaseColumn("123");

        // when
        List<String> decifradas = Stream.of(primeira, segunda).map(converter::convertToEntityAttribute).toList();

        // then
        assertThat(decifradas).containsExactly("123", "123");
    }

    @Test
    void deveDecifrarOQueFoiGravado_quandoOutraInstanciaUsaAMesmaChave() {
        // given
        // simula o servidor reiniciando e lendo o que ja estava no banco
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);
        CriptografiaConverter aposReinicio = new CriptografiaConverter(CHAVE);

        // when
        String decifrado = aposReinicio.convertToEntityAttribute(cifrado);

        // then
        assertThat(decifrado).isEqualTo(DOCUMENTO);
    }

    // ----- nulo e valor legado -----

    @Test
    void deveDevolverNulo_quandoCifraNulo() {
        // when
        String cifrado = converter.convertToDatabaseColumn(null);

        // then
        assertThat(cifrado).isNull();
    }

    @Test
    void deveDevolverNulo_quandoDecifraNulo() {
        // when
        String decifrado = converter.convertToEntityAttribute(null);

        // then
        assertThat(decifrado).isNull();
    }

    @Test
    void deveLerComoTextoPuro_quandoValorLegadoNaoTemPrefixo() {
        // when
        String lido = converter.convertToEntityAttribute("12345678900");

        // then
        assertThat(lido).isEqualTo("12345678900");
    }

    // ----- falhas ao decifrar -----

    @Test
    void deveFalharSemExporODocumento_quandoDecifraComOutraChave() {
        // given
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);
        CriptografiaConverter outraChave = new CriptografiaConverter(base64("fedcba9876543210fedcba9876543210"));

        // when
        Throwable erro = catchThrowable(() -> outraChave.convertToEntityAttribute(cifrado));

        // then
        assertThat(erro)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(DOCUMENTO);
    }

    @Test
    void deveFalharSemExporODocumento_quandoDadoGravadoFoiAdulterado() {
        // given
        // troca os ultimos 4 caracteres do valor gravado
        String cifrado = converter.convertToDatabaseColumn(DOCUMENTO);
        String adulterado = cifrado.substring(0, cifrado.length() - 4) + "AAAA";

        // when
        Throwable erro = catchThrowable(() -> converter.convertToEntityAttribute(adulterado));

        // then
        assertThat(erro)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(DOCUMENTO);
    }

    // ----- chave invalida -----

    @Test
    void deveRecusarAChave_quandoTamanhoNaoEh32Bytes() {
        // given
        String chaveCurta = base64("curta");

        // when
        Throwable erro = catchThrowable(() -> new CriptografiaConverter(chaveCurta));

        // then
        assertThat(erro)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void deveRecusarAChave_quandoNaoEhBase64() {
        // when
        Throwable erro = catchThrowable(() -> new CriptografiaConverter("###"));

        // then
        assertThat(erro)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    private static String base64(String valor) {
        return Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }
}
