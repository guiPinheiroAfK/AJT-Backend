CREATE TABLE usuarios (
                          id BIGSERIAL PRIMARY KEY,
                          nome VARCHAR(100) NOT NULL,
                          username VARCHAR(50) NOT NULL UNIQUE,
                          senha VARCHAR(255) NOT NULL,
                          role VARCHAR(20) NOT NULL,
                          ativo BOOLEAN NOT NULL DEFAULT TRUE,
                          ultimo_login TIMESTAMP,
                          criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE passageiros (
                             id BIGSERIAL PRIMARY KEY,
                             nome VARCHAR(100) NOT NULL,
                             tipo_documento VARCHAR(20) NOT NULL,
                             documento TEXT NOT NULL,
                             nacionalidade VARCHAR(50)
);

CREATE TABLE veiculos (
                          id BIGSERIAL PRIMARY KEY,
                          label VARCHAR(50) NOT NULL,
                          placa VARCHAR(10) NOT NULL UNIQUE,
                          capacidade INTEGER NOT NULL,
                          tipo VARCHAR(50),
                          marca VARCHAR(50)
);

CREATE TABLE motoristas (
                            id BIGSERIAL PRIMARY KEY,
                            nome VARCHAR(100) NOT NULL,
                            cnh VARCHAR(20) NOT NULL UNIQUE,
                            telefone VARCHAR(20),
                            latitude_atual DOUBLE PRECISION,
                            longitude_atual DOUBLE PRECISION
);