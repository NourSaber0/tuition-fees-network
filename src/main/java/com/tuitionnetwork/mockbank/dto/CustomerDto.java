package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomerDto(
        @JsonProperty("national_id") String nationalId,
        @JsonProperty("mobile") String mobile
) {}
