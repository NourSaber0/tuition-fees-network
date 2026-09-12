package com.tuitionnetwork.identity.dto.users;

import java.time.LocalDateTime;
import java.util.UUID;

public record SchoolUserSummaryDto(
        UUID id,
        String name,
        String email,
        String role,
        String status,
        LocalDateTime lastLogin,
        LocalDateTime createdAt
) {}
