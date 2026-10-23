package com.ajtbackend.repository;

import com.ajtbackend.model.PontoColeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PontoColetaRepository extends JpaRepository<PontoColeta, Long> {

    List<PontoColeta> findByTransferIdOrderByOrdemParadaAsc(Long transferId);
}
