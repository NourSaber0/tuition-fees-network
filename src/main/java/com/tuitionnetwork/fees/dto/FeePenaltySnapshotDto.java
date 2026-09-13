package com.tuitionnetwork.fees.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FeePenaltySnapshotDto(
        boolean applied,
        BigDecimal penaltyAmountEGP,
        LocalDateTime penaltyAppliedAt,
        boolean graceEnded,
        BigDecimal totalDueEGP
) {
}
