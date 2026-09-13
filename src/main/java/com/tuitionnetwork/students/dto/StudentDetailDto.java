package com.tuitionnetwork.students.dto;

import java.time.LocalDate;

public record StudentDetailDto(
        String id,
        String studentRef,
        String name,
        String grade,
        String section,
        String status,
        String nationalIdMasked,
        String parentName,
        String parentPhone,
        String parentEmail,
        StudentTotalsDto totals,
        LocalDate deactivatedDate,
        String deactivationReason
) {
}
