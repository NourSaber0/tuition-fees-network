package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MoiValidateRequest(
        @JsonProperty("national_id") String nationalId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("purpose") String purpose
) {}
