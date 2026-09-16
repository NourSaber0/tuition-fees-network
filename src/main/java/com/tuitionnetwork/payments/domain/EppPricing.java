package com.tuitionnetwork.payments.domain;

import com.tuitionnetwork.settings.dto.EppSettingsDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EppPricing {

    private EppPricing() {
    }

    public record Quote(
            BigDecimal principal,
            int tenorMonths,
            BigDecimal annualInterestRate,
            BigDecimal interestAmount,
            BigDecimal adminFee,
            BigDecimal totalPayable,
            BigDecimal monthlyInstalment
    ) {
    }

    public static Quote calculate(BigDecimal principal, int tenorMonths, EppSettingsDto settings) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal amount must be greater than zero");
        }
        if (tenorMonths <= 0) {
            throw new IllegalArgumentException("Tenor must be greater than zero");
        }

        BigDecimal scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP);
        
        // Ensure tenor is supported, else fallback to 0%? Actually just use 0 if missing.
        Integer ratePct = settings.interestRatePct() != null ? settings.interestRatePct().get(tenorMonths) : null;
        BigDecimal annualInterestRate;
        if (ratePct == null) {
            annualInterestRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        } else {
            annualInterestRate = BigDecimal.valueOf(ratePct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }

        BigDecimal adminFeeRate = BigDecimal.valueOf(settings.adminFeeRatePct()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal adminFeeCap = BigDecimal.valueOf(settings.adminFeeCapEGP()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal interestAmount = scaledPrincipal.multiply(annualInterestRate)
                .multiply(BigDecimal.valueOf(tenorMonths))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                
        BigDecimal adminFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (annualInterestRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal calculatedAdminFee = scaledPrincipal.multiply(adminFeeRate).setScale(2, RoundingMode.HALF_UP);
            adminFee = calculatedAdminFee.min(adminFeeCap);
        }

        BigDecimal totalPayable = scaledPrincipal.add(interestAmount).add(adminFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyInstalment = totalPayable.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);

        return new Quote(scaledPrincipal, tenorMonths, annualInterestRate, interestAmount, adminFee, totalPayable, monthlyInstalment);
    }
}
