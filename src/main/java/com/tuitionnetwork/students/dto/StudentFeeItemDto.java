package com.tuitionnetwork.students.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StudentFeeItemDto(
        String feeId,
        String name,
        String category,
        String term,
        LocalDate dueDate,
        BigDecimal originalAmountEGP,
        BigDecimal paidEGP,
        BigDecimal remainingEGP,
        String status
) {
}
