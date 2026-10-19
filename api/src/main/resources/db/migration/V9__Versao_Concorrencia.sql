-- ============================================================
--  V9__Versao_Concorrencia.sql
--  RNF008: varios usuarios editando ao mesmo tempo sem perder dados.
--  Cada tabela editavel ganha uma coluna "versao" (lock otimista do JPA, @Version):
--  todo UPDATE vira "... WHERE id = ? AND versao = ?" e soma 1 na versao.
--  Se outra pessoa salvou antes, nenhuma linha bate, e a API responde 409
--  em vez de sobrescrever a alteracao dela.
--  DEFAULT 0 preenche os registros que ja existem.
-- ============================================================

ALTER TABLE usuarios       ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE passageiros    ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE motoristas     ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE veiculos       ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ordens_servico ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE transfers      ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pontos_coleta  ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE paradas_os     ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
