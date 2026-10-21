-- ============================================================
--  V8__Auditoria_Por_Registro.sql
--  Historico de um registro so (HU11): GET /api/auditoria?tabela=transfers&registroId=7
--  filtra por tabela + registro e ordena por data. O indice da V7
--  cobre so (tabela, data); este cobre a consulta por registro.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_logs_tabela_registro_data
    ON logs_transfers (tabela_afetada, registro_id, data_hora DESC);
