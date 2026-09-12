package com.tuitionnetwork.identity.dto.users;

public record UpdateSchoolUserRequest(
        String name,
        String email,
        String role
) {}
