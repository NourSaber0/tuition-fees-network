package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;

public record EppSummaryDto(
        String planId,
        int tenor,
        BigDecimal monthlyEGP
) {
}
