package com.AJTBackend.repository;

import com.AJTBackend.model.ParadaOs;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParadaOsRepository extends JpaRepository<ParadaOs, Long> {

    // carrega os transfers na mesma query (evita N+1 ao montar transferIds)
    @EntityGraph(attributePaths = "transfers")
    List<ParadaOs> findByOrdemServicoIdOrderByOrdemParadaAsc(Long osId);

    @Override
    @EntityGraph(attributePaths = "transfers")
    Optional<ParadaOs> findById(Long id);
}
