package com.tuitionnetwork.billing.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.DeadlinePolicy;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeePriority;
import com.tuitionnetwork.billing.dto.ApplyPenaltiesResult;
import com.tuitionnetwork.billing.dto.FeeDeadlineSnapshot;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FeeDeadlineService {

    private final FeeLineRepository feeLineRepository;
    private final AuditLogRepository auditLogRepository;

    public FeeDeadlineService(FeeLineRepository feeLineRepository,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.feeLineRepository = feeLineRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Read-only view of where a fee line stands today. Never mutates or persists anything.
     * If a penalty has already been applied, the stored amount is echoed back (frozen) rather
     * than recomputed - satisfies the "no second 5%" idempotency guarantee at display time too.
     */
    public FeeDeadlineSnapshot computeSnapshot(FeeLine feeLine) {
        LocalDate today = LocalDate.now();
        BigDecimal outstanding = feeLine.getRemainingAmount() != null ? feeLine.getRemainingAmount() : BigDecimal.ZERO;
        FeePriority priority = DeadlinePolicy.priority(feeLine.getDueDate(), outstanding, today);

        BigDecimal penalty = feeLine.getPenaltyAppliedAt() != null
                ? feeLine.getPenaltyAmountEGP()
                : DeadlinePolicy.computePenalty(outstanding, feeLine.getDueDate(), today);

        return new FeeDeadlineSnapshot(
                feeLine.getDueDate(),
                priority,
                DeadlinePolicy.daysToDue(feeLine.getDueDate(), today),
                outstanding,
                penalty,
                feeLine.getPenaltyAppliedAt(),
                DeadlinePolicy.graceEnded(feeLine.getDueDate(), today),
                DeadlinePolicy.totalDue(outstanding, penalty)
        );
    }

    /**
     * Idempotently applies and persists the 5% late penalty if this fee line is overdue,
     * has an outstanding balance, and has never been penalized before. Safe to call on every
     * payment attempt and from the nightly batch job alike - a fee line that already has
     * penaltyAppliedAt set is left untouched and its stored amount is returned as-is.
     */
    @Transactional
    public BigDecimal applyPenaltyIfDue(FeeLine feeLine) {
        if (feeLine.getPenaltyAppliedAt() != null) {
            return feeLine.getPenaltyAmountEGP() != null ? feeLine.getPenaltyAmountEGP() : BigDecimal.ZERO;
        }

        BigDecimal outstanding = feeLine.getRemainingAmount() != null ? feeLine.getRemainingAmount() : BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        BigDecimal penalty = DeadlinePolicy.computePenalty(outstanding, feeLine.getDueDate(), today);

        if (penalty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        feeLine.setPenaltyAmountEGP(penalty);
        feeLine.setPenaltyAppliedAt(LocalDateTime.now());
        feeLineRepository.save(feeLine);
        return penalty;
    }

    /**
     * Authorized override of a fee line's due date (spec &sect;15 edge case). Recomputation of
     * priority/penalty happens naturally on the next read since neither is stored.
     */
    @Transactional
    public FeeDeadlineSnapshot updateDueDate(UUID feeLineId, LocalDate newDueDate, String reason, UUID actorId) {
        if (newDueDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueDate is required");
        }
        FeeLine feeLine = feeLineRepository.findById(feeLineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee line not found: " + feeLineId));

        LocalDate previousDueDate = feeLine.getDueDate();
        feeLine.setDueDate(newDueDate);
        feeLineRepository.save(feeLine);

        if (auditLogRepository != null) {
            String actorName = "system";
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                actorName = auth.getName();
            }
            auditLogRepository.save(new AuditLog(
                    actorId,
                    "BACK_OFFICE",
                    "FEE_DUE_DATE_CHANGED",
                    "FeeLine " + feeLineId + " due date changed from " + previousDueDate + " to " + newDueDate
                            + (reason != null && !reason.isBlank() ? " (" + reason + ")" : ""),
                    "WARNING",
                    "FeeLine",
                    feeLineId.toString(),
                    String.valueOf(previousDueDate),
                    String.valueOf(newDueDate),
                    null,
                    actorName
            ));
        }

        return computeSnapshot(feeLine);
    }

    /**
     * Nightly batch entry point (spec: POST /internal/fees/apply-penalties). Walks every overdue,
     * outstanding, not-yet-penalized fee line and applies the 5% once. Idempotent by construction -
     * findOverdueUnpenalized() only ever returns rows with penaltyAppliedAt IS NULL.
     */
    @Transactional
    public ApplyPenaltiesResult applyOverduePenalties() {
        List<FeeLine> candidates = feeLineRepository.findOverdueUnpenalized(LocalDate.now());
        BigDecimal totalApplied = BigDecimal.ZERO;
        int processed = 0;

        for (FeeLine feeLine : candidates) {
            BigDecimal penalty = applyPenaltyIfDue(feeLine);
            if (penalty.compareTo(BigDecimal.ZERO) > 0) {
                totalApplied = totalApplied.add(penalty);
                processed++;
            }
        }

        if (auditLogRepository != null && processed > 0) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "SYSTEM",
                    "BATCH_PENALTIES_APPLIED",
                    "Applied late penalties to " + processed + " fee lines totaling " + totalApplied + " EGP",
                    "INFO",
                    "FeeLine",
                    "batch",
                    null,
                    totalApplied.toPlainString(),
                    null,
                    "system"
            ));
        }

        return new ApplyPenaltiesResult(processed, totalApplied);
    }
}
