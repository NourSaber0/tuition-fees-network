package com.tuitionnetwork.ingestion.dto;

import java.time.LocalDateTime;

public record FeeUploadDetailDto(
        String uploadId,
        String status,
        int totalRows,
        int validRows,
        int invalidRows,
        int acceptedRows,
        int rejectedRows,
        LocalDateTime uploadedAt,
        String fileName
) {
}
