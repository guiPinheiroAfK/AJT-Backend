INSERT INTO usuarios (nome, username, senha, role, ativo, criado_em)
VALUES (
           'Administrador',
           'admin',
           '$2b$10$lz3qtm1L35Kl1ahM5gLKGuCXf.cdD2lZp8/2Bch1P6GAVwjwdMcKG',
           'ADMIN',
           TRUE,
           now()
       );