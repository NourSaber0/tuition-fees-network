package com.tuitionnetwork.reconciliation.service;

import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import com.tuitionnetwork.reconciliation.dto.*;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationExceptionRepository exceptionRepository;

    public ReconciliationServiceImpl(ReconciliationRunRepository runRepository, ReconciliationExceptionRepository exceptionRepository) {
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Override
    public ReconciliationSummaryDto getSummary() {
        long totalRuns = runRepository.count();
        long totalExceptions = exceptionRepository.count();
        long pendingExceptions = exceptionRepository.findByStatus("OPEN", Pageable.ofSize(1)).getTotalElements();
        return new ReconciliationSummaryDto(totalRuns, totalExceptions, pendingExceptions);
    }

    @Override
    public Page<ReconciliationRunDto> listRuns(Pageable pageable) {
        var page = runRepository.findAll(pageable);
        List<ReconciliationRunDto> dtos = page.stream().map(r -> new ReconciliationRunDto(r.getId(), r.getCreatedAt(), r.getStatus(), r.getTotalTransactions(), r.getMatchedCount(), r.getExceptionCount())).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    public Optional<ReconciliationRunDto> getRun(UUID id) {
        return runRepository.findById(id).map(r -> new ReconciliationRunDto(r.getId(), r.getCreatedAt(), r.getStatus(), r.getTotalTransactions(), r.getMatchedCount(), r.getExceptionCount()));
    }

    @Override
    public ReconciliationRunDto triggerRun() {
        ReconciliationRun run = new ReconciliationRun("COMPLETED");
        run.setTotalTransactions(0);
        run.setMatchedCount(0);
        run.setExceptionCount(0);
        run = runRepository.save(run);
        return new ReconciliationRunDto(run.getId(), run.getCreatedAt(), run.getStatus(), run.getTotalTransactions(), run.getMatchedCount(), run.getExceptionCount());
    }

    @Override
    public Page<ReconciliationExceptionDto> listExceptions(Pageable pageable, String status, String priority) {
        Page<ReconciliationException> page;
        if (status != null && !status.isBlank()) {
            page = exceptionRepository.findByStatus(status, pageable);
        } else {
            page = exceptionRepository.findAll(pageable);
        }
        List<ReconciliationExceptionDto> dtos = page.stream().map(e -> new ReconciliationExceptionDto(e.getId(), e.getReconciliationRunId(), e.getPaymentId(), e.getReason(), e.getStatus(), e.getAssignedTo(), e.getPriority(), e.getCreatedAt())).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    public Optional<ReconciliationExceptionDto> getException(UUID id) {
        return exceptionRepository.findById(id).map(e -> new ReconciliationExceptionDto(e.getId(), e.getReconciliationRunId(), e.getPaymentId(), e.getReason(), e.getStatus(), e.getAssignedTo(), e.getPriority(), e.getCreatedAt()));
    }

    @Override
    public ReconciliationExceptionDto resolveException(UUID id, ResolutionRequest request) {
        ReconciliationException ex = exceptionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Exception not found"));
        ex.setStatus(request.resultingStatus());
        ex.setReason(ex.getReason() + " | Resolution: " + request.resolutionNote());
        exceptionRepository.save(ex);
        return new ReconciliationExceptionDto(ex.getId(), ex.getReconciliationRunId(), ex.getPaymentId(), ex.getReason(), ex.getStatus(), ex.getAssignedTo(), ex.getPriority(), ex.getCreatedAt());
    }

    @Override
    public ReconciliationExceptionDto assignException(UUID id, AssignRequest request) {
        ReconciliationException ex = exceptionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Exception not found"));
        ex.setAssignedTo(request.assignee());
        exceptionRepository.save(ex);
        return new ReconciliationExceptionDto(ex.getId(), ex.getReconciliationRunId(), ex.getPaymentId(), ex.getReason(), ex.getStatus(), ex.getAssignedTo(), ex.getPriority(), ex.getCreatedAt());
    }

    @Override
    public List<String> listAssignees() {
        return List.of("recon_user_1", "recon_user_2", "recon_lead");
    }

    @Override
    public byte[] exportReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("runId,createdAt,status,total,matched,exceptions\n");
        for (ReconciliationRun run : runRepository.findAll()) {
            sb.append(String.format("%s,%s,%s,%d,%d,%d\n", run.getId(), run.getCreatedAt(), run.getStatus(), run.getTotalTransactions(), run.getMatchedCount(), run.getExceptionCount()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
