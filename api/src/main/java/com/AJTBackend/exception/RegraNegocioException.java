package com.AJTBackend.exception;

// 400 - requisicao valida no formato, mas que viola uma regra de negocio
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
