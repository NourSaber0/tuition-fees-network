package com.tuitionnetwork.students.dto;

public record StudentSearchDto(
        String id,
        String studentRef,
        String name,
        String grade,
        String section,
        String status
) {
}
