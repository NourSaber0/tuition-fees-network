package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDate;

public record SchoolReconciliationTransactionDto(
        String paymentId,
        String studentId,
        String feeId,
        long amountEGP,
        LocalDate date,
        String reconciliationStatus
) {}
