package com.tuitionnetwork.epp.dto;

public record UpdateEppPlanStatusRequest(
        String status,
        String reason
) {
}
