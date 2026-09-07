package com.tuitionnetwork.epp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EppPlanSummaryDto(
        UUID id,
        String payRef,
        String institution,
        String institutionType,
        String student,
        BigDecimal principalEGP,
        int tenor,
        BigDecimal interestRatePct,
        BigDecimal interestEGP,
        BigDecimal adminFeeEGP,
        BigDecimal totalEGP,
        BigDecimal monthlyEGP,
        int paidInstallments,
        String status,
        LocalDate startDate
) {
}
