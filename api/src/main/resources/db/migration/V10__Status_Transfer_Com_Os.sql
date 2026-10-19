-- ============================================================
--  V10__Status_Transfer_Com_Os.sql
--  Corrige dados gravados antes da regra do TransferService
--  (transfer com OS nao fica "aguardando OS"): quem ja esta
--  vinculado a uma OS e ainda marcava AGUARDANDO_OS passa a
--  CONFIRMADO, igual ao que a API faz daqui pra frente.
--  A versao sobe junto (lock otimista, V9): quem estava com a
--  tela aberta recebe 409 em vez de sobrescrever.
-- ============================================================

UPDATE transfers
   SET status = 'CONFIRMADO',
       versao = versao + 1
 WHERE os_id IS NOT NULL
   AND status = 'AGUARDANDO_OS';
