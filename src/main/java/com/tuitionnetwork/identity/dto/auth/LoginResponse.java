package com.tuitionnetwork.identity.dto.auth;

public record LoginResponse(
        boolean mfaRequired,
        String mfaToken,
        String otpChannel,
        String otpDestinationHint,
        int expiresInSeconds,
        int resendAvailableInSeconds
) {}
