-- ============================================================
--  V3__create_auditoria.sql
--  HU11 / RF014
--  Populada pela camada de serviço (não por trigger de banco).
-- ============================================================

CREATE TABLE logs_transfers (
    id             SERIAL PRIMARY KEY,
    tabela_afetada VARCHAR(50),
    registro_id    INT,
    mensagem       TEXT,
    data_hora      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);