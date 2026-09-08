package com.tuitionnetwork.payments.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record BackOfficePaymentRequest(
        @NotNull(message = "nationalId is required")
        String nationalId,

        @NotEmpty(message = "At least one fee must be selected")
        List<String> feeIds,

        @NotNull(message = "amountEGP is required")
        BigDecimal amountEGP,

        @NotNull(message = "method is required")
        String method,

        String creditPaymentType, // "full" or "epp"
        Integer eppTenor,         // required if creditPaymentType == "epp"
        String cardToken,
        String processedBy
) {
}
