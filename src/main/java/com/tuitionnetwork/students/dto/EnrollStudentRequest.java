package com.tuitionnetwork.students.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EnrollStudentRequest(
        @NotBlank(message = "studentRef is required")
        String studentRef,

        @NotBlank(message = "name is required")
        String name,

        String grade,
        String section,

        @NotBlank(message = "National ID is required. Please provide a 14-digit National ID.")
        @Pattern(regexp = "^\\d{14}$", message = "National ID must be exactly 14 numeric digits")
        String nationalId,

        String parentName,
        String parentPhone,
        String parentEmail
) {
}
