package com.tuitionnetwork.settings.dto;

import java.util.List;

/**
 * Reference data for {@code GET /settings/payment-statuses} (read-only).
 *
 * @param terminal true when no further transitions are allowed from this state
 */
public record PaymentStatusInfo(String status, String description, boolean terminal) {

    public static final List<PaymentStatusInfo> ALL = List.of(
            new PaymentStatusInfo("Successful", "Payment authorized and captured", true),
            new PaymentStatusInfo("Pending", "Awaiting bank authorization or confirmation", false),
            new PaymentStatusInfo("Failed", "Payment declined or errored", true),
            new PaymentStatusInfo("Refunded", "Successful payment subsequently refunded", true),
            new PaymentStatusInfo("Reversed", "Transaction reversed by bank", true),
            new PaymentStatusInfo("Voided", "Cancelled before capture", true)
    );
}
