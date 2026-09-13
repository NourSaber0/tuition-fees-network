package com.tuitionnetwork.ingestion.dto;

import java.time.LocalDateTime;

public record FeeUploadSummaryDto(
        String uploadId,
        LocalDateTime uploadedAt,
        String fileName,
        String status,
        int totalRows,
        int acceptedRows,
        int rejectedRows
) {
}
