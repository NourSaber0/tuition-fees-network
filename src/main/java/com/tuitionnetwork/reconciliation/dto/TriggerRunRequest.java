package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TriggerRunRequest(
        LocalDate date,
        UUID institutionId
) {}
