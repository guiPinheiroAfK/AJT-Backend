package com.AJTBackend.repository;

import com.AJTBackend.model.OrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {

    List<OrdemServico> findByStatus(String status);
}
