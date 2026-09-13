package com.tuitionnetwork.fees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFeeRequest(
        BigDecimal amountEGP,
        LocalDate dueDate,
        String name,
        String term,
        String remarks
) {
}
