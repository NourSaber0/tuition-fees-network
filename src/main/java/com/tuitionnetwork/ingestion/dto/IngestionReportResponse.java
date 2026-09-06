package com.tuitionnetwork.ingestion.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record IngestionReportResponse(
        UUID batchId,
        UUID institutionId,
        String fileName,
        int totalRows,
        int successfulRows,
        int failedRows,
        List<RowValidationError> validationErrors,
        LocalDateTime uploadedAt
) {
}
