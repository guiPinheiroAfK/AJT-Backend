package com.ajtbackend.config;

import com.ajtbackend.model.Passageiro;
import com.ajtbackend.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.crypto.Mac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
        // when
        String comHifenEMinusculas = hash.calcular("PASSAPORTE", "xp-998877");
        String limpo = hash.calcular("PASSAPORTE", "XP998877");

        // then
        assertThat(comHifenEMinusculas).isEqualTo(limpo);
    }

    @Test
    void deveDarOutroHash_quandoTipoOuChaveMudam() {
        // given
        String cpf = hash.calcular("CPF", "52998224725");

        // when
        String outroTipo = hash.calcular("RG", "52998224725");
        String outraChave = new DocumentoHash(OUTRA_CHAVE).calcular("CPF", "52998224725");

        // then
        assertThat(outroTipo).isNotEqualTo(cpf);
        assertThat(outraChave).isNotEqualTo(cpf);
    }

    @Test
    void naoDeveQuebrar_quandoTipoEDocumentoSaoNulos() {
        // when
        String semNada = hash.calcular(null, null);

        // then
        assertThat(semNada).hasSize(64);
    }

    @Test
    void deveLancarIllegalState_quandoAJvmNaoTemHmacSha256() {
        // given: provedor de criptografia indisponivel (nao acontece numa JVM normal)
        try (MockedStatic<Mac> mac = mockStatic(Mac.class)) {
            mac.when(() -> Mac.getInstance("HmacSHA256")).thenThrow(new NoSuchAlgorithmException("sem HmacSHA256"));

            // when
            Throwable erro = catchThrowable(() -> hash.calcular("CPF", "52998224725"));

            // then
            assertThat(erro).isInstanceOf(IllegalStateException.class).hasMessageContaining("hash do documento");
        }
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

        // when
        new DocumentoHashBackfill(repository, hash).run(null);

        // then: sem passageiros antigos, so consulta e sai
        verify(repository).findByDocumentoHashIsNull();
        verifyNoMoreInteractions(repository);
    }
}
