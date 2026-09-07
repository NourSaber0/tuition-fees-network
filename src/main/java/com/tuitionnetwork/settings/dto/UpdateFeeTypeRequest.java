package com.tuitionnetwork.settings.dto;

/** Partial update — any null field is left unchanged. */
public record UpdateFeeTypeRequest(
        String name,
        String code,
        Boolean taxable,
        Boolean active
) {
}
