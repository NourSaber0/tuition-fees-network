package com.tuitionnetwork.ingestion.dto;

public record FeeUploadResponseDto(
        String uploadId,
        String status,
        String message
) {
    public FeeUploadResponseDto(String uploadId, String status) {
        this(uploadId, status, null);
    }
}
