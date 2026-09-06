package com.tuitionnetwork.identity.dto;

import java.util.UUID;

public record ResolvedStudentDto(
        UUID studentId,
        String studentName,
        UUID institutionId,
        String institutionName
) {
}
