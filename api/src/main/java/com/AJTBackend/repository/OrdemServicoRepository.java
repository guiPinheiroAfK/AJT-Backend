package com.AJTBackend.repository;

import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.enums.StatusOrdemServico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {

    Page<OrdemServico> findByStatus(StatusOrdemServico status, Pageable pageable);
}
