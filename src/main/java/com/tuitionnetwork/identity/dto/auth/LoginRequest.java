package com.tuitionnetwork.identity.dto.auth;

public record LoginRequest(
        String username,
        String password,
        Boolean rememberMe
) {}
