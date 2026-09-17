package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackofficePaymentResponse(
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("status") String status,
        @JsonProperty("approved") boolean approved,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("captured_amount") BigDecimal capturedAmount,
        @JsonProperty("reference") String reference,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("source_id") String sourceId,
        @JsonProperty("source") PaymentSource source,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage,
        @JsonInclude(JsonInclude.Include.ALWAYS) @JsonProperty("epp_plan_id") String eppPlanId
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentSource(
            @JsonProperty("type") String type, // ACCOUNT or CARD
            @JsonProperty("product_type") String productType, // CURRENT, SAVINGS, CREDIT, DEBIT
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("masked_number") String maskedNumber,
            @JsonProperty("scheme") String scheme
    ) {}
}
