package com.tuitionnetwork.ingestion.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A single fee submission with its full row-level error report
 * ({@code GET /api/v1/institutions/{id}/fee-submissions/{submissionId}}).
 */
public record FeeSubmissionDetailDto(
        UUID submissionId,
        UUID institutionId,
        String fileName,
        int totalRows,
        int successfulRows,
        int failedRows,
        String status,
        LocalDateTime uploadedAt,
        List<FeeSubmissionRowErrorDto> errors
) {
}
