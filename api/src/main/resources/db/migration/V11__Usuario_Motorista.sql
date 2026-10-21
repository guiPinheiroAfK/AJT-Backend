-- ============================================================
--  V11__Usuario_Motorista.sql
--  HU07: o motorista ve as proprias ordens de servico do dia.
--  Pra saber quais OS sao "dele", o usuario de perfil MOTORISTA
--  passa a apontar pro cadastro de motorista (motoristas.id).
--  UNIQUE: um cadastro de motorista pertence a um usuario so.
--  ON DELETE SET NULL: excluir o motorista nao apaga o usuario,
--  so desfaz o vinculo (ele volta a nao ver nenhuma OS).
-- ============================================================

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS motorista_id BIGINT
        REFERENCES motoristas (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_motorista ON usuarios (motorista_id);
