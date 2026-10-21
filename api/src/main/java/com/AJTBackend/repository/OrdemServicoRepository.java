package com.AJTBackend.repository;

import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.enums.StatusOrdemServico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// JpaSpecificationExecutor: listagem com filtros opcionais (RF015, ver Especificacoes)
public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long>, JpaSpecificationExecutor<OrdemServico> {

    Page<OrdemServico> findByStatus(StatusOrdemServico status, Pageable pageable);

    // painel: quantas OS do dia em cada status (cada linha: status, total)
    @Query("SELECT o.status, COUNT(o) FROM OrdemServico o WHERE o.dataServico = :data GROUP BY o.status")
    List<Object[]> contarPorStatusNaData(@Param("data") LocalDate data);
}
