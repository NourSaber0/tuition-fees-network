package com.tuitionnetwork.reconciliation.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.reconciliation.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationService {

    ReconciliationSummaryDto getSummary();

    PageResponse<ReconciliationRunDto> listRuns(LocalDate date, String institution, String status, int page, int pageSize);

    Optional<ReconciliationRunDetailDto> getRun(UUID id);

    ReconciliationRunDto triggerRun(TriggerRunRequest request);

    PageResponse<ReconciliationExceptionDto> listExceptions(String status, String priority, String assignedTo, Boolean includeResolved, int page, int pageSize);

    Optional<ReconciliationExceptionDetailDto> getException(UUID id);

    ReconciliationExceptionDto resolveException(UUID id, ResolutionRequest request);

    ReconciliationExceptionDto assignException(UUID id, AssignRequest request);

    List<String> listAssignees();

    byte[] exportReport(String format);
}
