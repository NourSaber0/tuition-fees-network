package com.tuitionnetwork.reconciliation.repository;

import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ReconciliationExceptionRepository extends JpaRepository<ReconciliationException, UUID>, JpaSpecificationExecutor<ReconciliationException> {
    Page<ReconciliationException> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);
    long countByStatusNot(String status);
    List<ReconciliationException> findByReconciliationRunId(UUID reconciliationRunId);
}

