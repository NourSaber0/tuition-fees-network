package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AllocatedDueDetailDto(
        UUID feeLineId,
        String feeType,
        BigDecimal amountApplied,
        String academicPeriod
) {
}
