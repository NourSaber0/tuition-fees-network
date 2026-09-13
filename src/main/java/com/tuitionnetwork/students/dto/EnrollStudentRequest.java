package com.tuitionnetwork.students.dto;

import jakarta.validation.constraints.NotBlank;

public record EnrollStudentRequest(
        @NotBlank(message = "studentRef is required")
        String studentRef,

        @NotBlank(message = "name is required")
        String name,

        String grade,
        String section,
        String nationalId,
        String parentName,
        String parentPhone,
        String parentEmail
) {
}
