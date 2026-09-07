package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransactionDetailDto(
        UUID id,
        String institution,
        String institutionType,
        String student,
        String feeType,
        BigDecimal amountEGP,
        String method,
        String status,
        String bankRef,
        String settlementStatus,
        String reconStatus,
        LocalDateTime timestamp,
        PartialPaymentDto partial,
        String idempotencyKey,
        String channel,
        List<TimelineEventDto> timeline,
        List<AllocatedDueDetailDto> allocations
) {
}
