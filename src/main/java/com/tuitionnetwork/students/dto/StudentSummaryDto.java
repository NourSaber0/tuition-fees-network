package com.tuitionnetwork.students.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StudentSummaryDto(
        String id,
        String studentRef,
        String name,
        String grade,
        String section,
        BigDecimal totalFeesEGP,
        BigDecimal paidEGP,
        BigDecimal outstandingEGP,
        String status,
        LocalDate deactivatedDate,
        String deactivationReason
) {
}
