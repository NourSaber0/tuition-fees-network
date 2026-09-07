package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;

public record CustomerFeeItemDto(
        String id,
        String name,
        BigDecimal originalAmountEGP,
        BigDecimal paidEGP,
        BigDecimal remainingEGP,
        String status,
        boolean eligible
) {
}
