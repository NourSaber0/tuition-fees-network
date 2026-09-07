package com.tuitionnetwork.identity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A student enrolled at an institution, with their fee balance rolled up
 * ({@code GET /api/v1/institutions/{id}/students}).
 *
 * @param status "Paid" | "Partial" | "Unpaid"
 */
public record InstitutionStudentDto(
        UUID studentId,
        String fullName,
        LocalDate dateOfBirth,
        BigDecimal totalFeesEGP,
        BigDecimal paidEGP,
        BigDecimal outstandingEGP,
        String status
) {
}
