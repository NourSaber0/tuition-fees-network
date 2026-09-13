package com.tuitionnetwork.payments.dto;

import com.tuitionnetwork.students.dto.StudentPaymentItemDto;

import java.util.List;

public record SchoolPaymentListResponse(
        List<StudentPaymentItemDto> data,
        long total,
        int page,
        int pageSize,
        int totalPages
) {
}
