-- ============================================================
--  V1__create_base.sql
--  Entidades base do sistema: autenticação, passageiros e frota
--  HU01, HU02, HU03, HU09 / RF001-004, RF011 / RNF001, RNF007
-- ============================================================

-- ──────────────────────────────────────────────────────────────
--  USUÁRIOS (autenticação e controle de acesso)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE usuarios (
    id           BIGSERIAL    PRIMARY KEY,
    nome         VARCHAR(100) NOT NULL,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    senha        VARCHAR(255) NOT NULL,           -- hash BCrypt (RNF001)
    role         VARCHAR(20)  NOT NULL,           -- ADMIN, GERENTE, MOTORISTA, ATENDENTE
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_login TIMESTAMP,
    criado_em    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usuarios_username ON usuarios (username);

-- ──────────────────────────────────────────────────────────────
--  PASSAGEIROS
-- ──────────────────────────────────────────────────────────────
CREATE TABLE passageiros (
    id             SERIAL       PRIMARY KEY,
    nome           VARCHAR(100) NOT NULL,
    tipo_documento VARCHAR(20)  NOT NULL,
    documento      TEXT         NOT NULL,          -- AES-256-GCM (cifrado na camada de serviço)
    nacionalidade  VARCHAR(50)  DEFAULT 'Brasileira'
);

-- ──────────────────────────────────────────────────────────────
--  FROTA (motoristas e veículos)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE motoristas (
    id              SERIAL       PRIMARY KEY,
    nome            VARCHAR(100) NOT NULL,
    cnh             VARCHAR(20)  NOT NULL UNIQUE,
    telefone        VARCHAR(20),
    latitude_atual  DOUBLE PRECISION,
    longitude_atual DOUBLE PRECISION
);

CREATE TABLE veiculos (
    id         SERIAL      PRIMARY KEY,
    label      VARCHAR(50) NOT NULL,               -- ex: "Van 01"
    placa      VARCHAR(10) NOT NULL UNIQUE,
    capacidade INT         NOT NULL,
    tipo       VARCHAR(50),                        -- VAN, SEDAN, SUV...
    marca      VARCHAR(50)
);