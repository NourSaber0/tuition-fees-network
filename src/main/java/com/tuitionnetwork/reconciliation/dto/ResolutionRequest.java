package com.tuitionnetwork.reconciliation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ResolutionRequest(
        @JsonAlias("resultingStatus")
        String status,
        String assignedTo,
        String reason,
        String resolutionAction,
        String supportingReference,
        @JsonAlias("resolutionNote")
        String notes
) {
    public String effectiveStatus() {
        if (status != null && !status.isBlank()) {
            return status;
        }
        return "Resolved";
    }

    public String effectiveNotes() {
        if (notes != null && !notes.isBlank()) {
            return notes;
        }
        return reason;
    }
}
