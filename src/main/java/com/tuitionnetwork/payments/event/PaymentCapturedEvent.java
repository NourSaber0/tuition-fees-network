package com.tuitionnetwork.payments.event;

import com.tuitionnetwork.payments.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentCapturedEvent(
        UUID paymentId,
        UUID guardianId,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        String idempotencyKey,
        String transactionReference,
        String authCode,
        List<UUID> affectedFeeLineIds,
        Integer eppTenorMonths,
        LocalDateTime capturedAt
) {
    public PaymentCapturedEvent(
            UUID paymentId,
            UUID guardianId,
            BigDecimal totalAmount,
            PaymentMethod paymentMethod,
            String idempotencyKey,
            String transactionReference,
            String authCode,
            List<UUID> affectedFeeLineIds,
            LocalDateTime capturedAt
    ) {
        this(paymentId, guardianId, totalAmount, paymentMethod, idempotencyKey, transactionReference, authCode, affectedFeeLineIds, null, capturedAt);
    }
}
