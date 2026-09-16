package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackofficePaymentRequest(
        @JsonProperty("source_id") String sourceId,
        @JsonProperty("amount") AmountDto amount,
        @JsonProperty("reference") String reference,
        @JsonProperty("description") String description,
        @JsonProperty("capture") Boolean capture
) {}
