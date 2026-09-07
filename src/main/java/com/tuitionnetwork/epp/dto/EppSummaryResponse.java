package com.tuitionnetwork.epp.dto;

import java.math.BigDecimal;

public record EppSummaryResponse(
        long active,
        long completed,
        long defaulted,
        BigDecimal totalOutstandingEGP
) {
}
