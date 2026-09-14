package com.tuitionnetwork.notifications.dto;

import java.time.LocalDate;

public record SchoolNotificationDto(
        String id,
        String type,
        String title,
        String description,
        String studentName,
        String feeType,
        Long feeAmountEGP,
        LocalDate dueDate,
        Integer daysUntilDue,
        String notificationStatus,
        LocalDate date,
        String time,
        boolean read,
        String relatedId
) {}
