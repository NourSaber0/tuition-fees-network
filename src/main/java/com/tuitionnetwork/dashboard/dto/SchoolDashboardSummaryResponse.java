package com.tuitionnetwork.dashboard.dto;

public record SchoolDashboardSummaryResponse(
        String asOf,
        SchoolDashboardKpis kpis
) {}
