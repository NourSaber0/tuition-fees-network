package com.tuitionnetwork.reporting.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Result of {@code POST /reports/generate} and {@code GET /reports/jobs/{jobId}}. */
public record ReportJobResponse(
        UUID jobId,
        String reportId,
        String status,
        String filename,
        String downloadUrl,
        ReportPreview preview,
        LocalDateTime createdAt
) {
}
