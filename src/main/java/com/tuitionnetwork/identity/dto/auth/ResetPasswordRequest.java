package com.tuitionnetwork.identity.dto.auth;

public record ResetPasswordRequest(
        String token,
        String newPassword
) {}
