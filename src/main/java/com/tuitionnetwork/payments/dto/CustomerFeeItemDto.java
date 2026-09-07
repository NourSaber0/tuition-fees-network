package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerFeeItemDto(
        String id,
        String name,
        BigDecimal originalAmountEGP,
        BigDecimal paidEGP,
        BigDecimal remainingEGP,
        String status,
        boolean eligible,
        LocalDate dueDate,
        String priority,
        Long daysToDue,
        BigDecimal penaltyEGP,
        BigDecimal totalDueEGP
) {
}
