package com.AJTBackend.repository;

import com.AJTBackend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    // HU07: quem ja esta ligado a este cadastro de motorista (no maximo um, ver V11)
    Optional<Usuario> findByMotoristaId(Long motoristaId);
}