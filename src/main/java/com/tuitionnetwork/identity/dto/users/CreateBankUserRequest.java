package com.tuitionnetwork.identity.dto.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateBankUserRequest(
        String name,
        String email,
        String username,
        String role,
        String department,
        String password
) {
    public CreateBankUserRequest(String name, String email, String username, String role, String department) {
        this(name, email, username, role, department, null);
    }
}
