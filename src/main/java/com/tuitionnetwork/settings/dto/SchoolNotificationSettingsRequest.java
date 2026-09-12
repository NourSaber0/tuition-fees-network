package com.tuitionnetwork.settings.dto;

import java.util.Map;

public record SchoolNotificationSettingsRequest(
        Map<String, Boolean> channels
) {}
