package com.tuitionnetwork.notifications.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** {@code GET /notifications} — a page of the feed plus the live unread count. */
public record NotificationListResponse(
        List<BackOfficeNotificationDto> data,
        int page,
        int pageSize,
        long total,
        int totalPages,
        long unreadCount
) {
    public static NotificationListResponse of(Page<?> page,
                                              List<BackOfficeNotificationDto> data,
                                              long unreadCount) {
        return new NotificationListResponse(
                data, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), unreadCount);
    }
}
