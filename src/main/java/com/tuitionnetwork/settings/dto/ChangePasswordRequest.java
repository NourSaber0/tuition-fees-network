package com.tuitionnetwork.settings.dto;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {}
