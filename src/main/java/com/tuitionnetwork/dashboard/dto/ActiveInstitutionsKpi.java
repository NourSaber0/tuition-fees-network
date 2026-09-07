package com.tuitionnetwork.dashboard.dto;

public record ActiveInstitutionsKpi(
        long value,
        long schools,
        long universities,
        double trendPct
) {
}
