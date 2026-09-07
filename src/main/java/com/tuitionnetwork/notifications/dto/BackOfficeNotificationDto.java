package com.tuitionnetwork.notifications.dto;

import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifSeverity;
import com.tuitionnetwork.notifications.domain.NotifType;

import java.time.LocalDateTime;
import java.util.UUID;

public record BackOfficeNotificationDto(
        UUID id,
        NotifType type,
        NotifSeverity severity,
        String title,
        String body,
        String meta,
        boolean read,
        LocalDateTime createdAt,
        Action action
) {
    /** Call-to-action; null when the notification has no linked screen. */
    public record Action(String label, String screen, String entityId) {
    }

    public static BackOfficeNotificationDto from(BackOfficeNotification n) {
        Action action = n.getActionLabel() == null ? null
                : new Action(n.getActionLabel(), n.getActionScreen(), n.getActionEntityId());
        return new BackOfficeNotificationDto(
                n.getId(), n.getNotifType(), n.getSeverity(), n.getTitle(), n.getBody(),
                n.getMeta(), n.isReadFlag(), n.getCreatedAt(), action);
    }
}
