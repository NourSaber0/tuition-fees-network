package com.tuitionnetwork.notifications.service;

import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.dto.NotificationListResponse;

import java.util.UUID;

/** Back-office notification feed (Phase 8). Read + manage only; creation is via {@link BackOfficeNotificationPublisher}. */
public interface NotificationsService {

    NotificationListResponse list(NotifType type, boolean unreadOnly, int page, int size);

    long unreadCount();

    void markRead(UUID id);

    int markAllRead();

    void dismiss(UUID id);

    org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe();

    void broadcast(com.tuitionnetwork.notifications.domain.BackOfficeNotification notification);
}
