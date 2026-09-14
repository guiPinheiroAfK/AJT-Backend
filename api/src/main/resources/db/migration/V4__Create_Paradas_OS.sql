-- ============================================================
--  V4__Create_Paradas_OS.sql
--  Paradas de uma Ordem de Serviço, agrupando embarques e
--  desembarques de múltiplos transfers num mesmo ponto/horário.
-- ============================================================

CREATE TABLE paradas_os (
    id               SERIAL PRIMARY KEY,
    os_id            INT NOT NULL REFERENCES ordens_servico(id) ON DELETE CASCADE,
    ordem_parada     INT NOT NULL,
    local_parada     VARCHAR(100) NOT NULL,
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    horario_previsto TIME,
    acao             VARCHAR(50),                    -- ex: 'EMBARQUE', 'DESEMBARQUE'
    status_parada    VARCHAR(20) DEFAULT 'PENDENTE'
);

-- ──────────────────────────────────────────────────────────────
--  ASSOCIAÇÃO PARADA <-> TRANSFERS (N:N)
--  Quais transfers compõem cada parada.
-- ──────────────────────────────────────────────────────────────
CREATE TABLE parada_os_transfers (
    parada_os_id INT NOT NULL REFERENCES paradas_os(id) ON DELETE CASCADE,
    transfer_id  INT NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    PRIMARY KEY (parada_os_id, transfer_id)
);
