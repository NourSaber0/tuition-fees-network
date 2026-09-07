package com.tuitionnetwork.reconciliation.repository;

import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID>, JpaSpecificationExecutor<ReconciliationRun> {
}

