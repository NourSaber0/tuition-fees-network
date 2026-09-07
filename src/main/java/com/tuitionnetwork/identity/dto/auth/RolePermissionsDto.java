package com.tuitionnetwork.identity.dto.auth;

import java.util.List;

public record RolePermissionsDto(
        String role,
        List<String> permissions
) {}
