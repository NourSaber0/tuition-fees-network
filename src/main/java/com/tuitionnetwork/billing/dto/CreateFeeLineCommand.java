package com.tuitionnetwork.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFeeLineCommand(
        String nationalId,
        UUID studentId,
        String feeType,
        BigDecimal amount,
        String currency,
        String collectionPeriod,
        LocalDate dueDate,
        String rowIdempotencyKey
) {
    public CreateFeeLineCommand(String nationalId, UUID studentId, String feeType,
                                BigDecimal amount, String currency, String collectionPeriod,
                                LocalDate dueDate) {
        this(nationalId, studentId, feeType, amount, currency, collectionPeriod, dueDate, null);
    }
}
