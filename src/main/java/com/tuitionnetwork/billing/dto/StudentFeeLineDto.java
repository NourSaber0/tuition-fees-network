package com.tuitionnetwork.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StudentFeeLineDto(
        UUID feeLineId,
        UUID studentId,
        UUID institutionId,
        String feeType,
        String collectionPeriod,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String currency,
        String status,
        LocalDate dueDate
) {
}
