package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SelectedDueDto(
        UUID feeLineId,
        BigDecimal amountToPay
) {
}
