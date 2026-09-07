package com.tuitionnetwork.ingestion.dto;

/** A single rejected row within a fee submission. */
public record FeeSubmissionRowErrorDto(
        int rowNumber,
        String errorMessage,
        String rawRow
) {
}
