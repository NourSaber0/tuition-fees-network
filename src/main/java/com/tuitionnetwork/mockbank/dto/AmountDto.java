package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record AmountDto(
        @JsonProperty("value") BigDecimal value,
        @JsonProperty("currency") String currency
) {}
