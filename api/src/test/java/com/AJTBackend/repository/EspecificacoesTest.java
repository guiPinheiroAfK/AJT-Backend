package com.AJTBackend.repository;

import com.AJTBackend.exception.RegraNegocioException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Test
    void valorVazioNaoFiltra() {
        assertThat(Especificacoes.<Object>contem("nome", null)).isNull();
        assertThat(Especificacoes.<Object>contem("nome", "   ")).isNull();
        assertThat(Especificacoes.<Object>igual("status", null)).isNull();
        assertThat(Especificacoes.<Object>dataEntre("data", null, null)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void contemIgnoraMaiusculasEEscapaCuringasDigitados() {
        Expression<String> minusculo = mock(Expression.class);
        when(cb.lower(any())).thenReturn(minusculo);

        Especificacoes.<Object>contem("nome", "  Ana_100%\\ ").toPredicate(root, query, cb);

        verify(cb).like(minusculo, "%ana\\_100\\%\\\\%", '\\');
    }

    @Test
    void algumContemProcuraEmCadaCampo() {
        Specification<Object> spec = Especificacoes.algumContem("gru", "origem", "destino");

        spec.toPredicate(root, query, cb);

        verify(root).get("origem");
        verify(root).get("destino");
        verify(cb, times(2)).like(any(), anyString(), any(Character.class));
        verify(cb).or(any(jakarta.persistence.criteria.Predicate[].class));
    }

    @Test
    void igualNavegaPorCampoAninhado() {
        Especificacoes.igual("motorista.id", 5L).toPredicate(root, query, cb);

        verify(root).get("motorista");
        verify(path).get("id");
        verify(cb).equal(path, 5L);
    }

    @Test
    void periodoUsaOComparadorCertoEmCadaCaso() {
        LocalDate inicio = LocalDate.of(2026, 9, 1);
        LocalDate fim = LocalDate.of(2026, 9, 30);

        Especificacoes.dataEntre("data", null, fim).toPredicate(root, query, cb);
        Especificacoes.dataEntre("data", inicio, null).toPredicate(root, query, cb);
        Especificacoes.dataEntre("data", inicio, fim).toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(any(), any(LocalDate.class));
        verify(cb).greaterThanOrEqualTo(any(), any(LocalDate.class));
        verify(cb).between(any(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void periodoInvertidoEhRecusadoEOsOutrosPassam() {
        LocalDate dia = LocalDate.of(2026, 9, 20);

        assertThatThrownBy(() -> Especificacoes.validarPeriodo(dia, dia.minusDays(1)))
                .isInstanceOf(RegraNegocioException.class);
        assertThatCode(() -> Especificacoes.validarPeriodo(dia, dia)).doesNotThrowAnyException();
        assertThatCode(() -> Especificacoes.validarPeriodo(null, dia)).doesNotThrowAnyException();
        assertThatCode(() -> Especificacoes.validarPeriodo(dia, null)).doesNotThrowAnyException();
    }

    @Test
    void nenhumNaoDeixaPassarNada() {
        Especificacoes.nenhum().toPredicate(root, query, cb);

        verify(cb).disjunction();
        verify(cb, never()).conjunction();
    }
}
