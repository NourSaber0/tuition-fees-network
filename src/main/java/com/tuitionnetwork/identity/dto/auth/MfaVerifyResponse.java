package com.tuitionnetwork.identity.dto.auth;

public record MfaVerifyResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        BankUserDto user
) {}
