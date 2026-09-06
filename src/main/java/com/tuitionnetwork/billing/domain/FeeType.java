package com.tuitionnetwork.billing.domain;

public enum FeeType {
    TUITION("Tuition"),
    BUS("Bus subscription"),
    BOOKS("Books & materials"),
    ACTIVITIES("Activities");

    private final String displayName;

    FeeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
