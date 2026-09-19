package com.tuitionnetwork.ingestion.event;

import com.tuitionnetwork.billing.domain.FeeLine;

public record FeeCreatedEvent(FeeLine feeLine) {
}
