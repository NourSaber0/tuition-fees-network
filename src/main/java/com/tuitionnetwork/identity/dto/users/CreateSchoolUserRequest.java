package com.tuitionnetwork.identity.dto.users;

public record CreateSchoolUserRequest(
        String name,
        String email,
        String role,
        String password
) {}
