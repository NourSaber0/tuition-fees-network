package com.tuitionnetwork.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SchoolRecentPaymentDto(
        String id,
        String studentId,
        String studentName,
        UUID feeId,
        String feeName,
        BigDecimal amountEGP,
        String date,
        String status,
        boolean isPartial
) {}
