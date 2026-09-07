package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;

public record CollectionKpi(
        BigDecimal value,
        BigDecimal prevDayEGP,
        double trendPct
) {
}
