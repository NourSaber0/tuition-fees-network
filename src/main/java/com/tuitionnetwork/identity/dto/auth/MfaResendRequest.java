package com.tuitionnetwork.identity.dto.auth;

public record MfaResendRequest(
        String mfaToken
) {}
