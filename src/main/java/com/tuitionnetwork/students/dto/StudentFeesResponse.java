package com.tuitionnetwork.students.dto;

import java.util.List;

public record StudentFeesResponse(
        List<StudentFeeItemDto> data,
        StudentTotalsDto totals
) {
}
