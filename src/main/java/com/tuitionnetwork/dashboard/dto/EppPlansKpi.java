package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;

public record EppPlansKpi(
        long value,
        BigDecimal outstandingEGP,
        double trendPct
) {
}
