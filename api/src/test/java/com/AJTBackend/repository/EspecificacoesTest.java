package com.AJTBackend.repository;

import com.AJTBackend.exception.RegraNegocioException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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
import static org.assertj.core.api.Assertions.catchThrowable;
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
 * o resultado das mesmas pecas contra o postgres de verdade fica no FiltrosNoBancoRepositoryTest;
 * aqui fica o detalhe de qual comparador e escolhido.
 */
class EspecificacoesTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 20);

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

    @ParameterizedTest(name = "contem com \"{0}\" nao filtra")
    @NullSource
    @ValueSource(strings = {"", "   "})
    void naoDeveFiltrar_quandoTextoDoContemEstaVazio(String texto) {
        // when
        Specification<Object> spec = Especificacoes.contem("nome", texto);

        // then
        assertThat(spec).isNull();
    }

    @Test
    void naoDeveFiltrar_quandoValorDoIgualEhNulo() {
        // when
        Specification<Object> spec = Especificacoes.igual("status", null);

        // then
        assertThat(spec).isNull();
    }

    @Test
    void naoDeveFiltrar_quandoPeriodoNaoTemNenhumaPonta() {
        // when
        Specification<Object> spec = Especificacoes.dataEntre("data", null, null);

        // then
        assertThat(spec).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveUsarLikeEmMinusculasComCuringasEscapados_quandoTextoTemUnderlinePorcentoEBarra() {
        // given
        Expression<String> minusculo = mock(Expression.class);
        when(cb.lower(any())).thenReturn(minusculo);
        Specification<Object> spec = Especificacoes.contem("nome", "  Ana_100%\\ ");

        // when
        spec.toPredicate(root, query, cb);

        // then
        verify(cb).like(minusculo, "%ana\\_100\\%\\\\%", '\\');
    }

    @Test
    void deveProcurarEmCadaCampoComOu_quandoAlgumContemRecebeDoisCampos() {
        // given
        Specification<Object> spec = Especificacoes.algumContem("gru", "origem", "destino");

        // when
        spec.toPredicate(root, query, cb);

        // then
        verify(root).get("origem");
        verify(root).get("destino");
        verify(cb, times(2)).like(any(), anyString(), any(Character.class));
        verify(cb).or(any(Predicate[].class));
    }

    @Test
    void deveNavegarPeloCampoAninhado_quandoIgualRecebeCaminhoComPonto() {
        // given
        Specification<Object> spec = Especificacoes.igual("motorista.id", 5L);

        // when
        spec.toPredicate(root, query, cb);

        // then
        verify(root).get("motorista");
        verify(path).get("id");
        verify(cb).equal(path, 5L);
    }

    @Test
    void deveUsarMenorOuIgual_quandoPeriodoTemSoFim() {
        // given
        Specification<Object> spec = Especificacoes.dataEntre("data", null, DIA);

        // when
        spec.toPredicate(root, query, cb);

        // then
        verify(cb).lessThanOrEqualTo(any(), any(LocalDate.class));
        verify(cb, never()).greaterThanOrEqualTo(any(), any(LocalDate.class));
        verify(cb, never()).between(any(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void deveUsarMaiorOuIgual_quandoPeriodoTemSoInicio() {
        // given
        Specification<Object> spec = Especificacoes.dataEntre("data", DIA, null);

        // when
        spec.toPredicate(root, query, cb);

        // then
        verify(cb).greaterThanOrEqualTo(any(), any(LocalDate.class));
        verify(cb, never()).lessThanOrEqualTo(any(), any(LocalDate.class));
        verify(cb, never()).between(any(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void deveUsarBetween_quandoPeriodoTemAsDuasPontas() {
        // given
        Specification<Object> spec = Especificacoes.dataEntre("data", DIA.minusDays(19), DIA.plusDays(10));

        // when
        spec.toPredicate(root, query, cb);

        // then
        verify(cb).between(any(), any(LocalDate.class), any(LocalDate.class));
        verify(cb, never()).lessThanOrEqualTo(any(), any(LocalDate.class));
        verify(cb, never()).greaterThanOrEqualTo(any(), any(LocalDate.class));
    }

    @Test
    void deveLancarRegraNegocio_quandoInicioDoPeriodoEhDepoisDoFim() {
        // when
        Throwable erro = catchThrowable(() -> Especificacoes.validarPeriodo(DIA, DIA.minusDays(1)));

        // then
        assertThat(erro)
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining(DIA.toString())
                .hasMessageContaining("depois da final");
    }

    @ParameterizedTest(name = "periodo de {0} ate {1} e aceito")
    @MethodSource("periodosValidos")
    void naoDeveLancarErro_quandoPeriodoEhValidoOuAberto(LocalDate inicio, LocalDate fim) {
        // when
        Throwable erro = catchThrowable(() -> Especificacoes.validarPeriodo(inicio, fim));

        // then
        assertThat(erro).isNull();
    }

    @Test
    void deveUsarDisjuncao_quandoFiltroNaoDeixaPassarNada() {
        // given
        Specification<Object> spec = Especificacoes.nenhum();

        // when
        spec.toPredicate(root, query, cb);

        // then
        verify(cb).disjunction();
        verify(cb, never()).conjunction();
    }

    static Stream<Arguments> periodosValidos() {
        return Stream.of(
                Arguments.of(DIA, DIA),
                Arguments.of(null, DIA),
                Arguments.of(DIA, null));
    }
}
