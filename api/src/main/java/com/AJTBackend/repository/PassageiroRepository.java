package com.AJTBackend.repository;

import com.AJTBackend.model.Passageiro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassageiroRepository extends JpaRepository<Passageiro, Long> {

    Page<Passageiro> findByNacionalidadeIgnoreCase(String nacionalidade, Pageable pageable);
}
