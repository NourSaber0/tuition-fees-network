package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record EppCreateRequest(
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("card") CardDto card,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("tenor_months") Integer tenorMonths,
        @JsonProperty("product_name") String productName
) {}
