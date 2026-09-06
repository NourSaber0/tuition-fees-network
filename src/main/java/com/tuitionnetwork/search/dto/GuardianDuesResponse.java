package com.tuitionnetwork.search.dto;

import java.math.BigDecimal;
import java.util.List;

public record GuardianDuesResponse(
        String guardianName,
        BigDecimal totalOutstandingEGP,
        List<StudentDuesResponse> students
) {
}
