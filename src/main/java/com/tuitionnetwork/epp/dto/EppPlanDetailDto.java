package com.tuitionnetwork.epp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EppPlanDetailDto(
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
        LocalDate startDate,
        LocalDate firstPaymentDate,
        EppProgressDto progress
) {
    public EppPlanDetailDto(
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
            LocalDate startDate,
            EppProgressDto progress
    ) {
        this(id, payRef, institution, institutionType, student, principalEGP, tenor,
                interestRatePct, interestEGP, adminFeeEGP, totalEGP, monthlyEGP, paidInstallments,
                status, startDate, startDate != null ? startDate.plusMonths(1) : null, progress);
    }
}
