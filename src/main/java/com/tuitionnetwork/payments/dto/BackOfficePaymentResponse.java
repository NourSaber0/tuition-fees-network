package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        EppSummaryDto epp,
        BigDecimal originalFeeEGP,
        BigDecimal penaltyEGP,
        BigDecimal totalCollectedEGP,
        LocalDateTime penaltyAppliedAt
) {
}
