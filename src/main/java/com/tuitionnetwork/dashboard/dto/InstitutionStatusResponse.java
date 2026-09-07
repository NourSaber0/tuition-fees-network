package com.tuitionnetwork.dashboard.dto;

import java.util.List;

public record InstitutionStatusResponse(
        long schools,
        long universities,
        List<InstitutionStatusBreakdown> breakdown
) {
}
