package com.AJTBackend.config;

import com.AJTBackend.model.Passageiro;
import com.AJTBackend.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * o que testa: o hash do documento do passageiro (V12) — o mesmo documento com ou sem
 * pontuacao, espacos e maiusculas da o mesmo hash; tipo diferente ou chave diferente da
 * outro; o hash nao contem o numero. e o backfill que preenche os passageiros antigos.
 *
 * como rodar: teste unitario puro (repositorio mockado no backfill). "mvn test".
 *
 * por que existe: o documento e cifrado com IV aleatorio, entao este hash e a unica
 * forma de achar CPF duplicado; se a normalizacao falhar, "529.982.247-25" e
 * "52998224725" viram dois passageiros.
 */
class DocumentoHashTest {

    private static final String CHAVE = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OUTRA_CHAVE = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIzNDU=";

    private final DocumentoHash hash = new DocumentoHash(CHAVE);

    @Test
    void deveDarOMesmoHash_quandoODocumentoVemComOuSemPontuacao() {
        // when
        String comPontuacao = hash.calcular("CPF", "529.982.247-25");
        String semPontuacao = hash.calcular("cpf", " 52998224725 ");

        // then
        assertThat(comPontuacao).isEqualTo(semPontuacao).hasSize(64).doesNotContain("52998224725");
    }

    @Test
    void deveIgnorarMaiusculasEHifen_quandoDocumentoEhPassaporte() {
        // when / then
        assertThat(hash.calcular("PASSAPORTE", "xp-998877")).isEqualTo(hash.calcular("PASSAPORTE", "XP998877"));
    }

    @Test
    void deveDarOutroHash_quandoTipoOuChaveMudam() {
        // given
        String cpf = hash.calcular("CPF", "52998224725");

        // when / then
        assertThat(hash.calcular("RG", "52998224725")).isNotEqualTo(cpf);
        assertThat(new DocumentoHash(OUTRA_CHAVE).calcular("CPF", "52998224725")).isNotEqualTo(cpf);
    }

    @Test
    void naoDeveQuebrar_quandoTipoEDocumentoSaoNulos() {
        // when / then
        assertThat(hash.calcular(null, null)).hasSize(64);
    }

    @Test
    void devePreencherOHash_quandoBackfillAchaPassageiroAntigo() {
        // given
        PassageiroRepository repository = mock(PassageiroRepository.class);
        Passageiro antigo = Passageiro.builder().id(1L).tipoDocumento("CPF").documento("529.982.247-25").build();
        when(repository.findByDocumentoHashIsNull()).thenReturn(List.of(antigo));

        // when
        new DocumentoHashBackfill(repository, hash).run(null);

        // then
        assertThat(antigo.getDocumentoHash()).isEqualTo(hash.calcular("CPF", "52998224725"));
    }

    @Test
    void naoDeveFazerNada_quandoBackfillNaoAchaPendencias() {
        // given
        PassageiroRepository repository = mock(PassageiroRepository.class);
        when(repository.findByDocumentoHashIsNull()).thenReturn(List.of());

        // when / then: sem passageiros antigos, so consulta e sai
        new DocumentoHashBackfill(repository, hash).run(null);
    }
}
