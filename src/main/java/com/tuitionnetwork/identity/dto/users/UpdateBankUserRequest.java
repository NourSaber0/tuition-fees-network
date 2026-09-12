package com.tuitionnetwork.identity.dto.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateBankUserRequest(
        String name,
        String email,
        String username,
        String role,
        String department
) {
}
