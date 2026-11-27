package com.ajtbackend.exception;

// 409 - o registro foi alterado por outra pessoa depois que o cliente o carregou (RNF008)
public class ConflitoDeVersaoException extends RuntimeException {

    public ConflitoDeVersaoException(String recurso, Long id) {
        super(recurso + " " + id + " foi alterado por outro usuário. Recarregue os dados e tente de novo.");
    }
}
