package com.tuitionnetwork.reconciliation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record AssignRequest(
        @JsonAlias("assignee")
        String assignedTo
) {
    public String assignee() {
        return assignedTo;
    }
}
