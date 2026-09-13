package com.tuitionnetwork.fees.dto;

public record FeeOverdueInfoDto(
        boolean isOverdue,
        long daysOverdue
) {
}
