package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReconciliationExceptionDto(
        UUID id,
        UUID reconRowId,
        UUID paymentId,
        String txRef,
        String institution,
        String institutionType,
        Long bankAmountEGP,
        Long systemAmountEGP,
        Long schoolAmountEGP,
        Long differenceEGP,
        String type,
        LocalDate date,
        String status,
        String assignedTo,
        String priority,
        String txStatus,
        String payMethod,
        String bankRef,
        String bankStatus,
        LocalDate settlementDate,
        String feeRef,
        LocalDate collectionDate,
        String reason,
        String resolutionAction,
        String supportingReference,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
    public UUID runId() {
        return reconRowId;
    }
}
