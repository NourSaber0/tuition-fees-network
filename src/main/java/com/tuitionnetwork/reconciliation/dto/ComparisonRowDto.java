package com.tuitionnetwork.reconciliation.dto;

public record ComparisonRowDto(
        String source,
        String reference,
        Long amountEGP,
        String status,
        String timestamp
) {}
