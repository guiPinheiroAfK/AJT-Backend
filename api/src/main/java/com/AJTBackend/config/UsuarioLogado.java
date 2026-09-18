package com.AJTBackend.config;

import com.AJTBackend.model.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * unico ponto do projeto que le o usuario autenticado do SecurityContextHolder.
 * os services perguntam aqui ("quem e o usuario logado?", "ele e motorista?")
 * em vez de mexer no contexto do spring security cada um do seu jeito.
 */
@Component
public class UsuarioLogado {

    private static final String SISTEMA = "sistema";

    /** username do usuario autenticado, ou "sistema" quando nao ha ninguem logado. */
    public String usernameOuSistema() {
        Authentication auth = autenticacao();
        return auth != null ? auth.getName() : SISTEMA;
    }

    /** true se o usuario autenticado e exatamente o username informado. */
    public boolean ehUsuario(String username) {
        Authentication auth = autenticacao();
        return auth != null && username != null && username.equals(auth.getName());
    }

    /** true se o usuario autenticado tem o perfil informado. */
    public boolean temPerfil(Role perfil) {
        Authentication auth = autenticacao();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + perfil.name()));
    }

    private Authentication autenticacao() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
