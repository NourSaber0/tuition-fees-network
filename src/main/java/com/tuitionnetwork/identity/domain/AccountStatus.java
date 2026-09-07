package com.tuitionnetwork.identity.domain;

/**
 * Operational state of an approved institution account (US-11).
 * Only ACTIVE institutions collect fees.
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
