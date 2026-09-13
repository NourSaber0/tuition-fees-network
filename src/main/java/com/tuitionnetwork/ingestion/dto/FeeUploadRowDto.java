package com.tuitionnetwork.ingestion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeUploadRowDto(
        int rowNumber,
        String studentRef,
        String feeName,
        String category,
        BigDecimal amountEGP,
        LocalDate dueDate,
        String status,
        String errorReason
) {
}
