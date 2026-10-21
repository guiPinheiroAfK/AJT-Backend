package com.AJTBackend.service;

import com.AJTBackend.exception.ConflitoDeVersaoException;

/**
 * RNF008 nos cadastros: o cliente manda a versao que carregou na tela; se outra pessoa salvou
 * no meio tempo, a versao do banco ja subiu e a edicao e recusada com 409 em vez de sobrescrever.
 * versao ausente = cliente antigo, nao confere (o @Version ainda barra duas gravacoes simultaneas).
 * transfer e ordem de servico tem a mesma regra no proprio service.
 */
final class Versoes {

    private Versoes() {
    }

    static void conferir(Long versaoAtual, Long versaoDoCliente, String recurso, Long id) {
        if (versaoDoCliente != null && !versaoDoCliente.equals(versaoAtual)) {
            throw new ConflitoDeVersaoException(recurso, id);
        }
    }
}
