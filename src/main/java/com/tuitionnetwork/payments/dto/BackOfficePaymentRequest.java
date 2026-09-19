package com.tuitionnetwork.payments.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record BackOfficePaymentRequest(
        String nationalId,

        @NotEmpty(message = "At least one fee must be selected")
        List<String> feeIds,

        @NotNull(message = "amountEGP is required")
        BigDecimal amountEGP,

        @NotNull(message = "method is required")
        String method,

        String sourceId,          // e.g. "acc_mona_current", "card_mona_visa", "EXTERNAL_CARD", "POS_TERMINAL"
        String creditPaymentType, // "full" or "epp"
        Integer eppTenor,         // required if creditPaymentType == "epp"
        String cardToken,
        String processedBy,
        String cardNumber,
        String cardHolderName,
        Integer expiryMonth,
        Integer expiryYear,
        String cvv,
        String posTerminalId,
        String posAuthRef,
        String channel
) {
    public BackOfficePaymentRequest(
            String nationalId,
            List<String> feeIds,
            BigDecimal amountEGP,
            String method,
            String sourceId,
            String creditPaymentType,
            Integer eppTenor,
            String cardToken,
            String processedBy
    ) {
        this(nationalId, feeIds, amountEGP, method, sourceId, creditPaymentType, eppTenor, cardToken, processedBy,
                null, null, null, null, null, null, null, null);
    }
}
