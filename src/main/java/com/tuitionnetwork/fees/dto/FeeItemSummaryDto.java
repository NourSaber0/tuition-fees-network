package com.tuitionnetwork.fees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeItemSummaryDto(
        String id,
        String studentId,
        String studentName,
        String studentRef,
        String grade,
        String name,
        String category,
        String term,
        LocalDate dueDate,
        BigDecimal originalAmountEGP,
        BigDecimal paidEGP,
        BigDecimal remainingEGP,
        String status,
        boolean penaltyApplied,
        BigDecimal penaltyAmountEGP,
        BigDecimal totalDueEGP
) {
}
