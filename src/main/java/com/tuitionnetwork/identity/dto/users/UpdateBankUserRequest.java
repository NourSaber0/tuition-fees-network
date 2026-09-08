package com.tuitionnetwork.identity.dto.users;

public record UpdateBankUserRequest(
        String name,
        String email,
        String username,
        String role,
        String department
) {
}
