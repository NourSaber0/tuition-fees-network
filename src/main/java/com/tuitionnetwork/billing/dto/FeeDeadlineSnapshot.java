package com.tuitionnetwork.billing.dto;

import com.tuitionnetwork.billing.domain.FeePriority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeeDeadlineSnapshot(
        LocalDate dueDate,
        FeePriority priority,
        long daysToDue,
        BigDecimal outstandingEGP,
        BigDecimal penaltyEGP,
        LocalDateTime penaltyAppliedAt,
        boolean graceEnded,
        BigDecimal totalDueEGP
) {
}
