package com.tuitionnetwork.epp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EppScheduleInstallmentDto(
        int number,
        LocalDate dueDate,
        BigDecimal principalEGP,
        BigDecimal interestEGP,
        BigDecimal amountEGP,
        BigDecimal paidAmountEGP,
        String status
) {
}
