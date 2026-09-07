package com.tuitionnetwork.identity.domain;

/**
 * Lifecycle of an institution's registration application (US-06 .. US-10).
 * PENDING / UNDER_REVIEW are the only states from which an application
 * can be APPROVED or REJECTED.
 */
public enum RegistrationStatus {
    PENDING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED
}
