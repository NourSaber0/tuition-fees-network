package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;

public record SchoolKpiValue(
        BigDecimal value,
        Double trendPct
) {}
