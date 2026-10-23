package com.AJTBackend.repository;

import com.AJTBackend.exception.RegraNegocioException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/*
 * o que testa: as pecas de filtro das listagens (RF015) — filtro vazio nao filtra (null),
 * "contem" sem diferenciar maiusculas e tratando % e _ digitados como texto, campo aninhado
 * ("motorista.id"), periodo aberto em qualquer ponta e periodo invertido recusado.
 *
 * como rodar: teste unitario com Root/CriteriaBuilder mockados, conferindo qual metodo do
 * CriteriaBuilder cada filtro chama. sem banco. "mvn test".
 *
 * por que existe: um "%" digitado na busca viraria curinga e traria a base inteira; e um
 * periodo montado com o metodo errado (>= no lugar de <=) devolve a lista oposta.
 */
class EspecificacoesTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 9, 30);

    private Root<Object> root;
    private Path<Object> path;
    private CriteriaBuilder cb;
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class);
        path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        cb = mock(CriteriaBuilder.class, RETURNS_MOCKS);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void textoVazioNaoFiltra(String valor) {
        // When
        Specification<Object> spec = Especificacoes.contem("nome", valor);

        // Then
        assertThat(spec).isNull();
    }

    @Test
    void valorNuloOuPeriodoSemPontasNaoFiltram() {
        // When
        Specification<Object> igual = Especificacoes.igual("status", null);
        Specification<Object> periodo = Especificacoes.dataEntre("data", null, null);

        // Then
        assertThat(igual).isNull();
        assertThat(periodo).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void contemIgnoraMaiusculasEEscapaCuringasDigitados() {
        // Given
        Expression<String> minusculo = mock(Expression.class);
        when(cb.lower(any())).thenReturn(minusculo);
        Specification<Object> spec = Especificacoes.contem("nome", "  Ana_100%\\ ");

        // When
        spec.toPredicate(root, query, cb);

        // Then
        verify(cb).like(minusculo, "%ana\\_100\\%\\\\%", '\\');
    }

    @Test
    void algumContemProcuraEmCadaCampo() {
        // Given
        Specification<Object> spec = Especificacoes.algumContem("gru", "origem", "destino");

        // When
        spec.toPredicate(root, query, cb);

        // Then
        verify(root).get("origem");
        verify(root).get("destino");
        verify(cb, times(2)).like(any(), anyString(), any(Character.class));
        verify(cb).or(any(jakarta.persistence.criteria.Predicate[].class));
    }

    @Test
    void igualNavegaPorCampoAninhado() {
        // Given
        Specification<Object> spec = Especificacoes.igual("motorista.id", 5L);

        // When
        spec.toPredicate(root, query, cb);

        // Then
        verify(root).get("motorista");
        verify(path).get("id");
        verify(cb).equal(path, 5L);
    }

    @Test
    void periodoSoComFimUsaMenorOuIgual() {
        // Given
        Specification<Object> spec = Especificacoes.dataEntre("data", null, FIM);

        // When
        spec.toPredicate(root, query, cb);

        // Then
        verify(cb).lessThanOrEqualTo(any(), eq(FIM));
        verifyNoMoreInteractions(cb);
    }

    @Test
    void periodoSoComInicioUsaMaiorOuIgual() {
        // Given
        Specification<Object> spec = Especificacoes.dataEntre("data", INICIO, null);

        // When
        spec.toPredicate(root, query, cb);

        // Then
        verify(cb).greaterThanOrEqualTo(any(), eq(INICIO));
        verifyNoMoreInteractions(cb);
    }

    @Test
    void periodoComAsDuasPontasUsaBetween() {
        // Given
        Specification<Object> spec = Especificacoes.dataEntre("data", INICIO, FIM);

        // When
        spec.toPredicate(root, query, cb);

        // Then
        verify(cb).between(any(), eq(INICIO), eq(FIM));
        verifyNoMoreInteractions(cb);
    }

    @Test
    void periodoComInicioDepoisDoFimEhRecusado() {
        // When / Then
        assertThatThrownBy(() -> Especificacoes.validarPeriodo(FIM, INICIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("depois da final");
    }

    static Stream<Arguments> periodosValidos() {
        return Stream.of(
                Arguments.of(INICIO, INICIO),
                Arguments.of(INICIO, FIM),
                Arguments.of(null, FIM),
                Arguments.of(INICIO, null),
                Arguments.of(null, null));
    }

    @ParameterizedTest
    @MethodSource("periodosValidos")
    void periodoValidoOuAbertoEhAceito(LocalDate inicio, LocalDate fim) {
        // When / Then
        assertThatCode(() -> Especificacoes.validarPeriodo(inicio, fim)).doesNotThrowAnyException();
    }

    @Test
    void nenhumNaoDeixaPassarNada() {
        // Given
        Specification<Object> spec = Especificacoes.nenhum();

        // When
        spec.toPredicate(root, query, cb);

        // Then
        verify(cb).disjunction();
        verify(cb, never()).conjunction();
    }
}
