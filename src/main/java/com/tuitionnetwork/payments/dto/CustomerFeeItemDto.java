package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerFeeItemDto(
        String id,
        String studentName,
        String studentGrade,
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
    public CustomerFeeItemDto(
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
        this(id, null, null, name, originalAmountEGP, paidEGP, remainingEGP, status, eligible, dueDate, priority, daysToDue, penaltyEGP, totalDueEGP);
    }
}
