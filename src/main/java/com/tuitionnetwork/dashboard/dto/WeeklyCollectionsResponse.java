package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeeklyCollectionsResponse(
        String currency,
        LocalDate from,
        LocalDate to,
        List<WeeklyCollectionPoint> series,
        BigDecimal weekTotalEGP,
        BigDecimal dailyAvgEGP
) {
}
