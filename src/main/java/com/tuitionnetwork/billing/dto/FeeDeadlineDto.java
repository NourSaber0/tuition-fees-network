package com.tuitionnetwork.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FeeDeadlineDto(
        UUID feeLineId,
        LocalDate dueDate,
        String priority,
        long daysToDue,
        BigDecimal outstandingEGP,
        BigDecimal penaltyEGP,
        LocalDateTime penaltyAppliedAt,
        boolean graceEnded,
        BigDecimal totalDueEGP
) {
}
