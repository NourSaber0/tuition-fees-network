package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReconciliationRunDto(
        UUID id,
        String institution,
        String institutionType,
        LocalDate date,
        Integer txCount,
        Long bankAmountEGP,
        Long systemAmountEGP,
        Long schoolAmountEGP,
        String status,
        LocalDateTime createdAt,
        Integer totalTransactions,
        Integer matchedCount,
        Integer exceptionCount
) {}
