package com.AJTBackend.repository;

import com.AJTBackend.model.LogAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {

    Page<LogAuditoria> findByTabelaAfetada(String tabelaAfetada, Pageable pageable);
}
