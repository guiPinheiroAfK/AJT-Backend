package com.AJTBackend.repository;

import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Page<Transfer> findByStatus(StatusTransfer status, Pageable pageable);
}
