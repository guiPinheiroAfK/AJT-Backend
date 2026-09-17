package com.AJTBackend.dto.validacao;

/**
 * Grupo de validacao usado nos endpoints PATCH: campos obrigatorios podem
 * vir nulos (atualizacao parcial), mas se vierem precisam respeitar
 * tamanho/formato. Regras que valem para os dois casos declaram
 * groups = {Default.class, OnPatch.class}.
 */
public interface OnPatch {
}
