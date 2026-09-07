package com.tuitionnetwork.dashboard.dto;

public record RateKpi(
        long value,
        double ratePct,
        double trendPct
) {
}
