package com.tuitionnetwork.identity.dto;

import jakarta.validation.constraints.NotBlank;

/** Reject an institution application (US-10). A reason is mandatory. */
public record RejectInstitutionRequest(
        @NotBlank String reason,
        String notes
) {
}
