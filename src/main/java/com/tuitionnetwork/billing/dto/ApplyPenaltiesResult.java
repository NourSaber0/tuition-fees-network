package com.tuitionnetwork.billing.dto;

import java.math.BigDecimal;

public record ApplyPenaltiesResult(
        int processed,
        BigDecimal penaltiesAppliedEGP
) {
}
