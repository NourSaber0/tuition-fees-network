package com.tuitionnetwork.identity.dto.users;

public record CreateBankUserRequest(
        String name,
        String email,
        String username,
        String role,
        String department
) {
}
