package com.tuitionnetwork.students.dto;

import java.util.List;

public record StudentPaymentsResponse(
        List<StudentPaymentItemDto> data
) {
}
