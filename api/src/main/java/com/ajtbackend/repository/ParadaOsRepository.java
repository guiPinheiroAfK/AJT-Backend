package com.ajtbackend.repository;

import com.ajtbackend.model.ParadaOs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // HU07: paradas das OS de um motorista (o que o perfil MOTORISTA enxerga na listagem geral)
    Page<ParadaOs> findByOrdemServicoMotoristaId(Long motoristaId, Pageable pageable);
}
