package com.AJTBackend.repository;

import com.AJTBackend.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByStatus(String status);
}
