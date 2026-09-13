package com.tuitionnetwork.fees.dto;

import java.math.BigDecimal;

public record FeeStatsDto(
        BigDecimal totalInvoicedEGP,
        BigDecimal totalCollectedEGP,
        BigDecimal totalOutstandingEGP,
        BigDecimal totalOverdueEGP,
        long overdueCount,
        long totalStudentsWithOverdue,
        long activeFeeLinesCount
) {
}
