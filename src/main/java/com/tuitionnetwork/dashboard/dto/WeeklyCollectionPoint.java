package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklyCollectionPoint(
        LocalDate date,
        String label,
        BigDecimal amountEGP
) {
}
