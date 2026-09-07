package com.tuitionnetwork.identity.dto.auth;

public record LogoutRequest(
        String refreshToken
) {}
