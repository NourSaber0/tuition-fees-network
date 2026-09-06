package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAllocationResultDto(
        UUID allocationId,
        UUID feeLineId,
        BigDecimal amountApplied
) {
}
