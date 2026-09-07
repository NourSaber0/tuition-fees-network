package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;

public record BackOfficePaymentResponse(
        String transactionId,
        String status,
        BigDecimal amountPaidEGP,
        boolean isPartial,
        BigDecimal remainingBalanceEGP,
        String method,
        String bankRef,
        String authCode,
        String receiptRef,
        EppSummaryDto epp
) {
}
