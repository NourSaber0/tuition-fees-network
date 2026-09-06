package com.tuitionnetwork.payments.dto;

import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentSettleResponse(
        UUID paymentId,
        PaymentStatus status,
        String transactionReference,
        String authCode,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        String idempotencyKey,
        List<PaymentAllocationResultDto> allocations,
        LocalDateTime createdAt
) {
}
