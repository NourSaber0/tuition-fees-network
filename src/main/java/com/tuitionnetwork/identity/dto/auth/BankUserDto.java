package com.tuitionnetwork.identity.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BankUserDto(
        String id,
        String name,
        String initials,
        String email,
        String role,
        List<String> permissions,
        boolean mustChangePassword,
        String lastLoginAt,
        String schoolId,
        String schoolName
) {
    public BankUserDto(String id, String name, String initials, String email, String role,
                       List<String> permissions, boolean mustChangePassword, String lastLoginAt) {
        this(id, name, initials, email, role, permissions, mustChangePassword, lastLoginAt, null, null);
    }
}
