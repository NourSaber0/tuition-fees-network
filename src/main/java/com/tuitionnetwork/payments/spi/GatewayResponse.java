package com.tuitionnetwork.payments.spi;

import com.tuitionnetwork.payments.domain.PaymentStatus;

public record GatewayResponse(
        PaymentStatus status,
        String authCode,
        String transactionReference,
        String responseCode,
        String message
) {
    public GatewayResponse(PaymentStatus status, String authCode, String transactionReference) {
        this(status, authCode, transactionReference, "00", "Approved");
    }
}
