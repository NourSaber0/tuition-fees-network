package com.tuitionnetwork.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransactionTabCountsDto(
        @JsonProperty("All") long all,
        @JsonProperty("Successful") long successful,
        @JsonProperty("Pending") long pending,
        @JsonProperty("Failed") long failed
) {
}
