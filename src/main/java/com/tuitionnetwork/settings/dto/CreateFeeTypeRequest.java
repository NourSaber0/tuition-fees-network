package com.tuitionnetwork.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFeeTypeRequest(
        @NotBlank String name,
        @NotBlank String code,
        Boolean taxable,
        Boolean active
) {
    public boolean taxableOrDefault() {
        return taxable != null && taxable;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
