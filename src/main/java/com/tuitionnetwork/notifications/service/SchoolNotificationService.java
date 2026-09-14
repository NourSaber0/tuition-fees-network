package com.tuitionnetwork.notifications.service;

import com.tuitionnetwork.notifications.domain.SchoolNotification;
import com.tuitionnetwork.notifications.dto.SchoolNotificationDto;
import com.tuitionnetwork.notifications.dto.SchoolNotificationListResponse;
import com.tuitionnetwork.notifications.dto.SchoolNotificationPreferencesDto;

import java.time.LocalDate;
import java.util.UUID;

public interface SchoolNotificationService {

    SchoolNotificationListResponse getNotifications(
            UUID institutionId,
            String type,
            Boolean read,
            int page,
            int pageSize
    );

    long getUnreadCount(UUID institutionId);

    SchoolNotificationDto markAsRead(UUID institutionId, String idOrRef);

    int markAllAsRead(UUID institutionId);

    void dismiss(UUID institutionId, String idOrRef);

    SchoolNotificationListResponse getReminders(
            UUID institutionId,
            String status,
            int page,
            int pageSize
    );

    SchoolNotificationPreferencesDto getPreferences(UUID institutionId);

    SchoolNotificationPreferencesDto updatePreferences(
            UUID institutionId,
            SchoolNotificationPreferencesDto preferences,
            UUID actorId
    );

    SchoolNotification createNotification(
            UUID institutionId,
            String type,
            String title,
            String description,
            String studentName,
            UUID studentId,
            String feeType,
            Long feeAmountEGP,
            LocalDate dueDate,
            Integer daysUntilDue,
            String notificationStatus,
            String relatedId
    );
}
