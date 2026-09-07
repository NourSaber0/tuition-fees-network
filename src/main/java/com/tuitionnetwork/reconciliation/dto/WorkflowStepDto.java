package com.tuitionnetwork.reconciliation.dto;

public record WorkflowStepDto(
        String step,
        boolean done,
        String desc
) {}
