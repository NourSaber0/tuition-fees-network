package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardPaymentRequest(
        @JsonProperty("card") CardDto card,
        @JsonProperty("amount") AmountDto amount,
        @JsonProperty("order_reference") String orderReference,
        @JsonProperty("customer") CustomerDto customer,
        @JsonProperty("description") String description,
        @JsonProperty("capture") Boolean capture
) {}
