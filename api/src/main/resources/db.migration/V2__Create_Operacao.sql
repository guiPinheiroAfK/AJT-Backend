-- ============================================================
--  V2__create_operacao.sql
--  Núcleo operacional: ordens de serviço, transfers e coletas
--  HU04, HU05, HU06, HU07 / RF005-010, RF016
-- ============================================================

-- ──────────────────────────────────────────────────────────────
--  ORDENS DE SERVIÇO
--  Agrupa transfers por motorista + veículo + data (RF009).
-- ──────────────────────────────────────────────────────────────
CREATE TABLE ordens_servico (
    id           SERIAL PRIMARY KEY,
    data_servico DATE   NOT NULL,
    motorista_id INT REFERENCES motoristas(id),
    veiculo_id   INT REFERENCES veiculos(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'ABERTA'
);

-- ──────────────────────────────────────────────────────────────
--  TRANSFERS
--  os_id fica NULL até o transfer ser agrupado numa OS.
-- ──────────────────────────────────────────────────────────────
CREATE TABLE transfers (
    id             SERIAL       PRIMARY KEY,
    data_transfer  DATE         NOT NULL,
    hora_transfer  TIME         NOT NULL,
    origem         VARCHAR(100) NOT NULL,
    destino        VARCHAR(100) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'AGUARDANDO_OS',
    valor_base     DECIMAL(10,2),
    valor_original DECIMAL(10,2),                  -- valor na moeda de origem (RF016)
    moeda_origem   VARCHAR(10),
    os_id          INT REFERENCES ordens_servico(id)
);

-- ──────────────────────────────────────────────────────────────
--  PONTOS DE COLETA
--  Múltiplos pontos ordenados dentro de UM transfer (RF008).
-- ──────────────────────────────────────────────────────────────
CREATE TABLE pontos_coleta (
    id               SERIAL PRIMARY KEY,
    transfer_id      INT REFERENCES transfers(id) ON DELETE CASCADE,
    local_coleta     VARCHAR(100) NOT NULL,
    ordem_parada     INT,                          -- nullable: nem todo ponto tem ordem fixa
    horario_previsto TIME,
    latitude         DECIMAL(10,7) NOT NULL,
    longitude         DECIMAL(10,7) NOT NULL
);

-- ──────────────────────────────────────────────────────────────
--  ASSOCIAÇÃO TRANSFER <-> PASSAGEIRO (N:N)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE transfer_passageiros (
    transfer_id   INT NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    passageiro_id INT NOT NULL REFERENCES passageiros(id) ON DELETE CASCADE,
    PRIMARY KEY (transfer_id, passageiro_id)
);