package com.tuitionnetwork.ingestion.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of an institution's fee-upload history
 * ({@code GET /api/v1/institutions/{id}/fee-submissions}, US-14).
 *
 * @param status "PROCESSED" (no failed rows) | "PARTIAL" | "REJECTED" (all rows failed)
 */
public record FeeSubmissionSummaryDto(
        UUID submissionId,
        String fileName,
        int totalRows,
        int successfulRows,
        int failedRows,
        String status,
        LocalDateTime uploadedAt
) {
}
