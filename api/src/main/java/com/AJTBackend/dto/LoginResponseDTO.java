package com.AJTBackend.dto;

import com.AJTBackend.model.enums.Role;

public record LoginResponseDTO(
        String token,
        String tipo,        // sempre "Bearer"
        long expiraEm,      // segundos ate o token expirar
        String username,
        Role role,
        boolean trocarSenha // front deve redirecionar pra tela de troca de senha
) {}
