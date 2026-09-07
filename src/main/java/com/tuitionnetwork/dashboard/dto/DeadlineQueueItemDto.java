package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DeadlineQueueItemDto(
        UUID feeLineId,
        String institution,
        String student,
        String feeType,
        LocalDate dueDate,
        String priority,
        long daysToDue,
        BigDecimal outstandingEGP,
        BigDecimal penaltyEGP,
        BigDecimal totalDueEGP
) {
}
