package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;

/** One of a customer's CIB accounts, as returned by the bank gateway ({@code GET /customers/accounts}). */
public record CibAccountDto(
        String accountNumber,
        String accountType,
        BigDecimal balanceEGP,
        String currency,
        String status
) {
}
