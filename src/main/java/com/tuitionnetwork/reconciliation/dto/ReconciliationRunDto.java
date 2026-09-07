package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReconciliationRunDto(UUID id, LocalDateTime createdAt, String status, int totalTransactions, int matchedCount, int exceptionCount) {}
