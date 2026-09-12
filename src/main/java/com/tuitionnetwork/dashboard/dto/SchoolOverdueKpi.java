package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;

public record SchoolOverdueKpi(
        BigDecimal value,
        long overdueFeeCount,
        Double trendPct
) {}
