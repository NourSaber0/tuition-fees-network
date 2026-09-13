package com.tuitionnetwork.students.dto;

import java.math.BigDecimal;

public record StatementTransactionDto(
        String date,
        String type, // INVOICE | PAYMENT
        String reference,
        String description,
        BigDecimal debitEGP,
        BigDecimal creditEGP,
        BigDecimal runningBalanceEGP
) {
}
