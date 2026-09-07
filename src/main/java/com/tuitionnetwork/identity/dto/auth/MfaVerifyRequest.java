package com.tuitionnetwork.identity.dto.auth;

public record MfaVerifyRequest(
        String mfaToken,
        String code
) {}
