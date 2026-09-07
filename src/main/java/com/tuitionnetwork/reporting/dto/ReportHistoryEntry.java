package com.tuitionnetwork.reporting.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Row shape for {@code GET /reports/history}. */
public record ReportHistoryEntry(
        UUID jobId,
        String reportId,
        String format,
        String filename,
        int rowCount,
        LocalDateTime createdAt
) {
}
