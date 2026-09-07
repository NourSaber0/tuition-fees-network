package com.tuitionnetwork.payments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EppPricing {

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.14");
    private static final BigDecimal ADMIN_FEE_RATE = new BigDecimal("0.01");
    private static final BigDecimal ADMIN_FEE_CAP = new BigDecimal("500.00");

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

    public static Quote calculate(BigDecimal principal, int tenorMonths) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal amount must be greater than zero");
        }
        if (tenorMonths <= 0) {
            throw new IllegalArgumentException("Tenor must be greater than zero");
        }

        BigDecimal scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualInterestRate;
        BigDecimal interestAmount;
        BigDecimal adminFee;

        if (tenorMonths == 3) {
            annualInterestRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            interestAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            adminFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            annualInterestRate = ANNUAL_RATE.setScale(4, RoundingMode.HALF_UP);
            interestAmount = scaledPrincipal.multiply(annualInterestRate)
                    .multiply(BigDecimal.valueOf(tenorMonths))
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal calculatedAdminFee = scaledPrincipal.multiply(ADMIN_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
            adminFee = calculatedAdminFee.min(ADMIN_FEE_CAP);
        }

        BigDecimal totalPayable = scaledPrincipal.add(interestAmount).add(adminFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyInstalment = totalPayable.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);

        return new Quote(scaledPrincipal, tenorMonths, annualInterestRate, interestAmount, adminFee, totalPayable, monthlyInstalment);
    }
}
