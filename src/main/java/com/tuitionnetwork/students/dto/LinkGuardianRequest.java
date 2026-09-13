package com.tuitionnetwork.students.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkGuardianRequest(
        @NotBlank(message = "name is required")
        String name,
        String email,
        String phone,
        String relationship,
        Boolean primaryGuardian
) {
}
