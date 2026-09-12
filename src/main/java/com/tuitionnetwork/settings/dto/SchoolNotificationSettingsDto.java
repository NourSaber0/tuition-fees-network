package com.tuitionnetwork.settings.dto;

import java.util.Map;

public record SchoolNotificationSettingsDto(
        Map<String, Boolean> channels
) {
    public static SchoolNotificationSettingsDto of(boolean inApp, boolean email) {
        return new SchoolNotificationSettingsDto(Map.of("inApp", inApp, "email", email));
    }
}
