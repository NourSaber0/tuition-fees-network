package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionDto(
        UUID id,
        String institution,
        String institutionType,
        String student,
        String feeType,
        BigDecimal amountEGP,
        String method,
        String status,
        String bankRef,
        String settlementStatus,
        String reconStatus,
        LocalDateTime timestamp,
        PartialPaymentDto partial,
        String idempotencyKey,
        String channel,
        LocalDate dueDate,
        String priority,
        Long daysToDue,
        BigDecimal outstandingEGP,
        BigDecimal penaltyEGP,
        LocalDateTime penaltyAppliedAt,
        Boolean graceEnded,
        BigDecimal totalDueEGP,
        BigDecimal penaltyPaidEGP,
        BigDecimal penaltyRemainingEGP
) {
    public TransactionDto(
            UUID id,
            String institution,
            String institutionType,
            String student,
            String feeType,
            BigDecimal amountEGP,
            String method,
            String status,
            String bankRef,
            String settlementStatus,
            String reconStatus,
            LocalDateTime timestamp,
            PartialPaymentDto partial,
            String idempotencyKey,
            String channel,
            LocalDate dueDate,
            String priority,
            Long daysToDue,
            BigDecimal outstandingEGP,
            BigDecimal penaltyEGP,
            LocalDateTime penaltyAppliedAt,
            Boolean graceEnded,
            BigDecimal totalDueEGP
    ) {
        this(id, institution, institutionType, student, feeType, amountEGP, method, status, bankRef,
                settlementStatus, reconStatus, timestamp, partial, idempotencyKey, channel, dueDate,
                priority, daysToDue, outstandingEGP, penaltyEGP, penaltyAppliedAt, graceEnded, totalDueEGP,
                null, null);
    }
}
