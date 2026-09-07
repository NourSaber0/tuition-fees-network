package com.tuitionnetwork.epp.dto;

public record EppProgressDto(
        int paidInstallments,
        int totalInstallments,
        double percent
) {
}
