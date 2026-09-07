package com.tuitionnetwork.reconciliation.service;

import com.tuitionnetwork.reconciliation.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationService {
    ReconciliationSummaryDto getSummary();
    Page<ReconciliationRunDto> listRuns(Pageable pageable);
    Optional<ReconciliationRunDto> getRun(UUID id);
    ReconciliationRunDto triggerRun();
    Page<ReconciliationExceptionDto> listExceptions(Pageable pageable, String status, String priority);
    Optional<ReconciliationExceptionDto> getException(UUID id);
    ReconciliationExceptionDto resolveException(UUID id, ResolutionRequest request);
    ReconciliationExceptionDto assignException(UUID id, AssignRequest request);
    List<String> listAssignees();
    byte[] exportReport();
}
