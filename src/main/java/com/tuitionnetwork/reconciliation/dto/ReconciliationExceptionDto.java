package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReconciliationExceptionDto(UUID id, UUID runId, UUID paymentId, String reason, String status, String assignedTo, String priority, LocalDateTime createdAt) {}
