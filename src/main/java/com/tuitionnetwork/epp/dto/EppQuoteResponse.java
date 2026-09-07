package com.tuitionnetwork.epp.dto;

import java.math.BigDecimal;

public record EppQuoteResponse(
        BigDecimal principalEGP,
        int tenor,
        BigDecimal interestRatePct,
        BigDecimal interestEGP,
        BigDecimal adminFeeEGP,
        BigDecimal totalEGP,
        BigDecimal monthlyEGP
) {
}
