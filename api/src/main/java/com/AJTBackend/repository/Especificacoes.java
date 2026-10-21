package com.AJTBackend.repository;

import com.AJTBackend.exception.RegraNegocioException;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/**
 * pecas de filtro reaproveitadas pelas listagens (RF015).
 * cada metodo devolve null quando o valor nao foi informado, e o Specification.allOf
 * simplesmente ignora o null: filtro vazio = nao filtra por aquele campo.
 * campos aninhados usam ponto: "motorista.id".
 */
public final class Especificacoes {

    private static final char ESCAPE = '\\';

    private Especificacoes() {
    }

    /** texto contido no campo, sem diferenciar maiusculas ("ana" acha "Ana Paula"). */
    public static <T> Specification<T> contem(String campo, String valor) {
        return algumContem(valor, campo);
    }

    /** texto contido em pelo menos um dos campos (ex: origem OU destino). */
    public static <T> Specification<T> algumContem(String valor, String... campos) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String padrao = "%" + escaparLike(valor.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(Arrays.stream(campos)
                .map(campo -> cb.like(cb.lower(caminho(root, campo)), padrao, ESCAPE))
                .toArray(Predicate[]::new));
    }

    public static <T> Specification<T> igual(String campo, Object valor) {
        if (valor == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(caminho(root, campo), valor);
    }

    /** periodo fechado nas duas pontas; qualquer uma pode faltar (so "a partir de" ou so "ate"). */
    public static <T> Specification<T> dataEntre(String campo, LocalDate inicio, LocalDate fim) {
        if (inicio == null && fim == null) {
            return null;
        }
        return (root, query, cb) -> {
            Path<LocalDate> data = caminho(root, campo);
            if (inicio == null) {
                return cb.lessThanOrEqualTo(data, fim);
            }
            if (fim == null) {
                return cb.greaterThanOrEqualTo(data, inicio);
            }
            return cb.between(data, inicio, fim);
        };
    }

    /** "de" depois de "ate" e erro de quem chamou: 400 em vez de uma lista vazia sem explicacao. */
    public static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial (" + inicio + ") é depois da final (" + fim + ").");
        }
    }

    /** filtro que nao deixa passar nada (ex: motorista sem cadastro vinculado ao usuario). */
    public static <T> Specification<T> nenhum() {
        return (root, query, cb) -> cb.disjunction();
    }

    @SuppressWarnings("unchecked")
    private static <Y> Path<Y> caminho(Root<?> root, String campo) {
        Path<?> path = root;
        for (String parte : campo.split("\\.")) {
            path = path.get(parte);
        }
        return (Path<Y>) path;
    }

    // % e _ digitados pelo usuario sao texto, nao curinga do LIKE
    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
