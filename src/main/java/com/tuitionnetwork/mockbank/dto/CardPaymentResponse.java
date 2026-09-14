package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardPaymentResponse(
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("status") String status,
        @JsonProperty("approved") boolean approved,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("captured_amount") BigDecimal capturedAmount,
        @JsonProperty("card") CardInfo card,
        @JsonProperty("auth_code") String authCode,
        @JsonProperty("rrn") String rrn,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
    public record CardInfo(
            @JsonProperty("masked_number") String maskedNumber,
            @JsonProperty("scheme") String scheme,
            @JsonProperty("type") String type
    ) {}
}
