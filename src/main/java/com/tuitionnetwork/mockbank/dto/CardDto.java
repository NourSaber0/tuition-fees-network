package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardDto(
        @JsonProperty("number") String number,
        @JsonProperty("holder_name") String holderName,
        @JsonProperty("expiry_month") Integer expiryMonth,
        @JsonProperty("expiry_year") Integer expiryYear,
        @JsonProperty("cvv") String cvv
) {}
