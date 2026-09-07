package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DeadlineSummaryResponse(
        long dueToday,
        long dueThisWeek,
        long urgent,
        long overdue,
        BigDecimal penaltiesAppliedEGP,
        List<DeadlineQueueItemDto> priorityQueue
) {
}
