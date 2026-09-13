package com.tuitionnetwork.fees.dto;

import java.math.BigDecimal;

public record FeePaymentHistoryDto(
        String paymentId,
        String date,
        BigDecimal amountEGP,
        String status,
        String method
) {
}
