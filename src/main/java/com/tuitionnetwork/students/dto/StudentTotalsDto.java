package com.tuitionnetwork.students.dto;

import java.math.BigDecimal;

public record StudentTotalsDto(
        BigDecimal totalFeesEGP,
        BigDecimal totalPaidEGP,
        BigDecimal totalOutstandingEGP
) {
    public BigDecimal paidEGP() {
        return totalPaidEGP;
    }
}

