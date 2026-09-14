package com.tuitionnetwork.notifications.dto;

import java.util.Map;

public record SchoolNotificationPreferencesDto(
        Map<String, Boolean> channels
) {
    public static SchoolNotificationPreferencesDto of(boolean inApp, boolean email) {
        return new SchoolNotificationPreferencesDto(Map.of("inApp", inApp, "email", email));
    }
}
