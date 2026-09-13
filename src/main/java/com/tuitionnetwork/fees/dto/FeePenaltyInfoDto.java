package com.tuitionnetwork.fees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeePenaltyInfoDto(
        LocalDate dueDate,
        String priority,
        long daysToDue,
        BigDecimal outstandingEGP,
        BigDecimal penaltyAmountEGP,
        LocalDateTime penaltyAppliedAt,
        boolean graceEnded,
        BigDecimal totalDueEGP
) {
}
