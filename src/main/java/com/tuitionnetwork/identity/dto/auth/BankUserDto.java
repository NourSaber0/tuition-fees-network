package com.tuitionnetwork.identity.dto.auth;

import java.util.List;

public record BankUserDto(
        String id,
        String name,
        String initials,
        String email,
        String role,
        List<String> permissions,
        boolean mustChangePassword,
        String lastLoginAt
) {}
