package com.tuitionnetwork.settings.dto;

/** Notification configuration (Settings › Notification Settings). */
public record NotificationSettingsDto(Events events, Channels channels) {

    public record Events(
            boolean failedPayments,
            boolean reconExceptions,
            boolean schoolUploadErrors,
            boolean newSchoolReg,
            boolean systemAlerts,
            boolean dailySummary
    ) {
    }

    public record Channels(
            boolean inApp,
            boolean email,
            boolean sms,
            boolean slack
    ) {
    }

    public static NotificationSettingsDto defaults() {
        return new NotificationSettingsDto(
                new Events(true, true, true, true, true, true),
                new Channels(true, true, false, false));
    }
}
