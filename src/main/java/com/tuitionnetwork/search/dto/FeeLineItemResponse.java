package com.tuitionnetwork.search.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FeeLineItemResponse(
        UUID feeLineId,
        String feeType,
        String period,
        BigDecimal totalAmount,
        BigDecimal remainingAmount,
        String status
) {
}
