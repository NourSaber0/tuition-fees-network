package com.tuitionnetwork.identity.domain;

/**
 * State of the institution's data-integration channel (CSV upload / partner API).
 * Managed by the institution-integration module; surfaced read-only here.
 */
public enum IntegrationStatus {
    NOT_INTEGRATED,
    PENDING,
    INTEGRATED,
    FAILED
}
