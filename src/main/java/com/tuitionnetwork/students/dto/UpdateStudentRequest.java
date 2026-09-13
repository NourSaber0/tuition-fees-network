package com.tuitionnetwork.students.dto;

public record UpdateStudentRequest(
        String studentRef,
        String name,
        String grade,
        String section,
        String parentName,
        String parentPhone,
        String parentEmail
) {
}
