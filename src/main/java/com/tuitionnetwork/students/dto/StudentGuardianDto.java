package com.tuitionnetwork.students.dto;

public record StudentGuardianDto(
        String id,
        String name,
        String email,
        String phone,
        String relationship,
        boolean primaryGuardian
) {
}
