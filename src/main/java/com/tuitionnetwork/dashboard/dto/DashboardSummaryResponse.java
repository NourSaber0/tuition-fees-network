package com.tuitionnetwork.dashboard.dto;

import java.time.LocalDateTime;

public record DashboardSummaryResponse(
        LocalDateTime asOf,
        DashboardKpis kpis
) {
}
