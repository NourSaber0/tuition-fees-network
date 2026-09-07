package com.tuitionnetwork.dashboard.dto;

public record InstitutionStatusBreakdown(
        String label,
        long count,
        double pct
) {
}
