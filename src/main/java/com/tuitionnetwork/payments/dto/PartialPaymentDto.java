package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;

public record PartialPaymentDto(
        BigDecimal originalAmountEGP,
        BigDecimal previouslyPaidEGP
) {
}
