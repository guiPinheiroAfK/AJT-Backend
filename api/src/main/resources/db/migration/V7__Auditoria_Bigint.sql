-- ============================================================
--  V7__Auditoria_Bigint.sql
--  Alinha a tabela de auditoria (V3) ao padrao BIGINT das demais
--  tabelas: registro_id guarda o id de qualquer entidade (BIGSERIAL)
--  e nao pode estourar um INT. Tambem ganha indice pra consulta
--  por recurso, ordenada por data.
-- ============================================================

ALTER TABLE logs_transfers
    ALTER COLUMN id          TYPE BIGINT,
    ALTER COLUMN registro_id TYPE BIGINT;

ALTER SEQUENCE logs_transfers_id_seq AS BIGINT;

CREATE INDEX IF NOT EXISTS idx_logs_tabela_data ON logs_transfers (tabela_afetada, data_hora DESC);
