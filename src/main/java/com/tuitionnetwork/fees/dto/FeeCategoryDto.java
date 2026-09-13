package com.tuitionnetwork.fees.dto;

public record FeeCategoryDto(
        String code,
        String displayName,
        int priority
) {
}
