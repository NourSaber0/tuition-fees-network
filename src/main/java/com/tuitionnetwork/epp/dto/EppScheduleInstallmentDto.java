package com.tuitionnetwork.epp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EppScheduleInstallmentDto(
        int number,
        LocalDate dueDate,
        BigDecimal principalEGP,
        BigDecimal interestEGP,
        BigDecimal amountEGP,
        BigDecimal paidAmountEGP,
        String status,
        LocalDate paidDate
) {
    @JsonProperty("installmentNumber")
    public int installmentNumber() {
        return number;
    }

    public EppScheduleInstallmentDto(int number,
                                     LocalDate dueDate,
                                     BigDecimal principalEGP,
                                     BigDecimal interestEGP,
                                     BigDecimal amountEGP,
                                     BigDecimal paidAmountEGP,
                                     String status) {
        this(number, dueDate, principalEGP, interestEGP, amountEGP, paidAmountEGP, status, null);
    }
}
