package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SchoolPaymentAllocationItemDto(
        String feeId,
        String feeName,
        String feeCategory,
        LocalDate dueDate,
        BigDecimal originalAmountEGP,
        BigDecimal previouslyPaidEGP,
        BigDecimal outstandingEGP,
        BigDecimal allocatedEGP,
        BigDecimal remainingAfterEGP,
        String feeStatus,
        Integer priority,
        boolean isOverdue
) {
}
