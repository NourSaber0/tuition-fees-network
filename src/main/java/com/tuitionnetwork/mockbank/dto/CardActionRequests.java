package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class CardActionRequests {

    public record Card3dsRequest(
            @JsonProperty("otp") String otp
    ) {}

    public record CardCaptureRequest(
            @JsonProperty("amount") BigDecimal amount
    ) {}

    public record CardRefundRequest(
            @JsonProperty("amount") BigDecimal amount
    ) {}
}
