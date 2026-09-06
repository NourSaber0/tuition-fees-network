package com.tuitionnetwork.ingestion.dto;

public record RowValidationError(
        int lineNumber,
        String nationalId,
        String reason,
        String rawLine
) {
}
