package com.tuitionnetwork.billing.dto;

import java.time.LocalDate;

public record UpdateDueDateRequest(
        LocalDate dueDate,
        String reason
) {
}
