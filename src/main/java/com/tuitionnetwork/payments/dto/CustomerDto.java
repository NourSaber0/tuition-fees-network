package com.tuitionnetwork.payments.dto;

public record CustomerDto(
        String name,
        String nationalIdMasked,
        String institution,
        String institutionType,
        String grade
) {
}
