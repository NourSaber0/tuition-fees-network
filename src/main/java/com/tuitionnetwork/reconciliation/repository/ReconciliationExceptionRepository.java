package com.tuitionnetwork.reconciliation.repository;

import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationExceptionRepository extends JpaRepository<ReconciliationException, UUID> {
    Page<ReconciliationException> findByStatus(String status, Pageable pageable);
}
