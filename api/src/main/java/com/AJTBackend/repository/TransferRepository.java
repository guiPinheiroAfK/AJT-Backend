package com.AJTBackend.repository;

import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// JpaSpecificationExecutor: listagem com filtros opcionais (RF015, ver Especificacoes)
public interface TransferRepository extends JpaRepository<Transfer, Long>, JpaSpecificationExecutor<Transfer> {

    Page<Transfer> findByStatus(StatusTransfer status, Pageable pageable);

    // painel: quantos transfers do dia em cada status, numa query so (cada linha: status, total)
    @Query("SELECT t.status, COUNT(t) FROM Transfer t WHERE t.dataTransfer = :data GROUP BY t.status")
    List<Object[]> contarPorStatusNaData(@Param("data") LocalDate data);

    // faturamento (em BRL) de um periodo: soma o valor base, sem contar o status ignorado (cancelado)
    @Query("SELECT COALESCE(SUM(t.valorBase), 0) FROM Transfer t "
            + "WHERE t.dataTransfer BETWEEN :inicio AND :fim AND t.status <> :ignorado")
    BigDecimal somarValorBaseNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                                       @Param("ignorado") StatusTransfer ignorado);
}
