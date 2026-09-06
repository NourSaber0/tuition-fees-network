package com.tuitionnetwork.search.dto;

import java.util.List;
import java.util.UUID;

public record StudentDuesResponse(
        UUID studentId,
        String studentName,
        String institutionName,
        List<FeeLineItemResponse> dues
) {
}
