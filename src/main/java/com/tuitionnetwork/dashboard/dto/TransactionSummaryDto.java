package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionSummaryDto(
        UUID id,
        String institution,
        String student,
        String feeType,
        BigDecimal amountEGP,
        String method,
        String status,
        LocalDateTime timestamp
) {
}
