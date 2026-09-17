-- ============================================================
--  V6__Seguranca_E_Indices.sql
--  - Controle de troca de senha (senha inicial / temporaria)
--  - Invalidacao de tokens emitidos antes da ultima troca de senha
--  - Indices nas FKs e colunas usadas em filtros
-- ============================================================

ALTER TABLE usuarios
    ADD COLUMN trocar_senha      BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN senha_alterada_em TIMESTAMP;

-- Admin do seed (V5) ainda com a senha padrao precisa trocar no primeiro acesso
UPDATE usuarios
   SET trocar_senha = TRUE
 WHERE username = 'admin'
   AND senha = '$2b$10$lz3qtm1L35Kl1ahM5gLKGuCXf.cdD2lZp8/2Bch1P6GAVwjwdMcKG';

-- UNIQUE(username) ja cria indice; este era redundante
DROP INDEX IF EXISTS idx_usuarios_username;

CREATE INDEX IF NOT EXISTS idx_transfers_os_id          ON transfers (os_id);
CREATE INDEX IF NOT EXISTS idx_transfers_status         ON transfers (status);
CREATE INDEX IF NOT EXISTS idx_transfers_data           ON transfers (data_transfer, hora_transfer);
CREATE INDEX IF NOT EXISTS idx_ordens_servico_status    ON ordens_servico (status, data_servico);
CREATE INDEX IF NOT EXISTS idx_ordens_servico_motorista ON ordens_servico (motorista_id);
CREATE INDEX IF NOT EXISTS idx_ordens_servico_veiculo   ON ordens_servico (veiculo_id);
CREATE INDEX IF NOT EXISTS idx_pontos_coleta_transfer   ON pontos_coleta (transfer_id, ordem_parada);
CREATE INDEX IF NOT EXISTS idx_paradas_os_os            ON paradas_os (os_id, ordem_parada);
CREATE INDEX IF NOT EXISTS idx_parada_os_transfers_tr   ON parada_os_transfers (transfer_id);
CREATE INDEX IF NOT EXISTS idx_transfer_passageiros_pas ON transfer_passageiros (passageiro_id);
CREATE INDEX IF NOT EXISTS idx_passageiros_nacionalidade ON passageiros (UPPER(nacionalidade)); -- IgnoreCase do Spring Data gera upper()
