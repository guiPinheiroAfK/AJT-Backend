package com.AJTBackend.repository;

import com.AJTBackend.model.ParadaOs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParadaOsRepository extends JpaRepository<ParadaOs, Long> {

    List<ParadaOs> findByOrdemServicoIdOrderByOrdemParadaAsc(Long osId);
}
