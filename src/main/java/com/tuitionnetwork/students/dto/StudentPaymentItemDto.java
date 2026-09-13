package com.tuitionnetwork.students.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StudentPaymentItemDto(
        String id,
        String studentId,
        String studentName,
        String feeId,
        String feeName,
        LocalDate feeDueDate,
        Integer feePriority,
        BigDecimal amountEGP,
        String method,
        String date,
        String time,
        String status,
        String reconciliation,
        boolean isPartial,
        BigDecimal originalFeeAmountEGP,
        BigDecimal remainingAfterEGP
) {
}
