package com.tuitionnetwork.identity.dto;

import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.IntegrationStatus;

import java.util.UUID;

/**
 * Integration channel status (US-13). The rich payload (endpoint, protocol, sync
 * events) has no data source yet, so this returns the status flag plus a message;
 * {@code configured} is true only once an INTEGRATED institution has real sync data.
 */
public record InstitutionIntegrationDto(
        UUID institutionId,
        IntegrationStatus status,
        String message,
        boolean configured
) {
    public static InstitutionIntegrationDto from(Institution i) {
        IntegrationStatus status = i.getIntegrationStatus();
        String message = switch (status) {
            case INTEGRATED -> "Integration is active. Data sync configuration is not yet exposed via the API.";
            case PENDING -> "Integration setup is in progress. Awaiting API configuration and end-to-end testing.";
            case FAILED -> "The connection to this institution has failed. Contact CIB IT support to restore service.";
            case NOT_INTEGRATED -> "This institution has not been enrolled in the CIB integration program.";
        };
        return new InstitutionIntegrationDto(i.getId(), status, message, false);
    }
}
