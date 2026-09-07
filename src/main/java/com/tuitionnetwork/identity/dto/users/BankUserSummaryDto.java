package com.tuitionnetwork.identity.dto.users;

import java.time.LocalDateTime;
import java.util.UUID;

public record BankUserSummaryDto(
        UUID id,
        String name,
        String username,
        String email,
        String role,
        String status,
        LocalDateTime lastLogin,
        String department,
        LocalDateTime createdAt
) {
}
