-- ============================================================
--  V12__Passageiro_Documento_Hash.sql
--  O documento do passageiro e cifrado com IV aleatorio (LGPD), entao
--  o mesmo CPF nunca gera o mesmo texto no banco e nao da pra comparar.
--  documento_hash guarda um HMAC-SHA256 de "TIPO:NUMERO" (numero sem
--  pontuacao), calculado pela API: igual pro mesmo documento, sem
--  revelar o numero. O PassageiroService usa pra recusar duplicados.
--  Sem UNIQUE: bancos que ja tem duplicados gravados antes desta
--  versao continuariam quebrando a migracao. Os registros antigos
--  sao preenchidos na subida da API (DocumentoHashBackfill).
-- ============================================================

ALTER TABLE passageiros
    ADD COLUMN IF NOT EXISTS documento_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_passageiros_documento_hash ON passageiros (documento_hash);
