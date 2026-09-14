package com.tuitionnetwork.notifications.dto;

import java.util.List;

public record SchoolNotificationListResponse(
        List<SchoolNotificationDto> data,
        long unreadCount,
        long total,
        int page,
        int pageSize,
        int totalPages
) {}
