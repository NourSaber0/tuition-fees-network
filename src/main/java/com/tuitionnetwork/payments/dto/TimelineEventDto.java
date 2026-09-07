package com.tuitionnetwork.payments.dto;

import java.time.LocalDateTime;

public record TimelineEventDto(
        String label,
        LocalDateTime time,
        boolean done
) {
}
