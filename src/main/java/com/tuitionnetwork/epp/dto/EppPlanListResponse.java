package com.tuitionnetwork.epp.dto;

import java.util.List;

public record EppPlanListResponse(
        List<EppPlanSummaryDto> data,
        int page,
        int pageSize,
        long total,
        int totalPages
) {
}
