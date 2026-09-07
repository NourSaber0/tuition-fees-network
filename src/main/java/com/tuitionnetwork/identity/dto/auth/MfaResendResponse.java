package com.tuitionnetwork.identity.dto.auth;

public record MfaResendResponse(
        int expiresInSeconds,
        int resendAvailableInSeconds
) {}
