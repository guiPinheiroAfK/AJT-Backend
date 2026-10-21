package com.AJTBackend.repository;

import com.AJTBackend.model.Passageiro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// JpaSpecificationExecutor: listagem com filtros opcionais (RF015, ver Especificacoes)
public interface PassageiroRepository extends JpaRepository<Passageiro, Long>, JpaSpecificationExecutor<Passageiro> {

    Page<Passageiro> findByNacionalidadeIgnoreCase(String nacionalidade, Pageable pageable);

    // passageiros vinculados a um transfer (a relacao vive no lado do Transfer)
    @Query("select p from Transfer t join t.passageiros p where t.id = :transferId order by p.nome")
    List<Passageiro> findByTransferId(@Param("transferId") Long transferId);
}
