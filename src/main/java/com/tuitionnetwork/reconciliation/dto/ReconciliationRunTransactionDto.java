package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReconciliationRunTransactionDto(
        UUID id,
        String txRef,
        String studentName,
        String institution,
        Long amountEGP,
        String method,
        String status,
        LocalDateTime timestamp
) {}
