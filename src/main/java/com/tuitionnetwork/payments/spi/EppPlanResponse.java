package com.tuitionnetwork.payments.spi;

import java.math.BigDecimal;

public record EppPlanResponse(
        BigDecimal principal,
        int tenorMonths,
        BigDecimal annualInterestRate,
        BigDecimal interestAmount,
        BigDecimal adminFee,
        BigDecimal totalPayable,
        BigDecimal monthlyInstalment
) {
}
