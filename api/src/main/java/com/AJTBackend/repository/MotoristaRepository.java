package com.AJTBackend.repository;

import com.AJTBackend.model.Motorista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

// JpaSpecificationExecutor: listagem com filtros opcionais (RF015, ver Especificacoes)
public interface MotoristaRepository extends JpaRepository<Motorista, Long>, JpaSpecificationExecutor<Motorista> {

    Optional<Motorista> findByCnh(String cnh);

    boolean existsByCnh(String cnh);
}