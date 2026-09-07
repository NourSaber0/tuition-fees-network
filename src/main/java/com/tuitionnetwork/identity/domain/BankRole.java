package com.tuitionnetwork.identity.domain;

import java.util.List;
import java.util.Optional;

public enum BankRole {
    BANK_ADMIN(
            "bank-admin",
            "Bank Admin",
            List.of("dashboard", "schools", "transactions", "reconciliation",
                    "epp", "reports", "notifications", "audit-logs", "users", "settings")
    ),
    BANK_OPERATIONS(
            "bank-operations",
            "Operations",
            List.of("dashboard", "schools", "transactions", "reconciliation",
                    "epp", "notifications")
    ),
    BANK_FINANCE(
            "bank-finance",
            "Finance",
            List.of("dashboard", "transactions", "reconciliation",
                    "epp", "reports", "notifications")
    ),
    BANK_RECONCILIATION(
            "bank-reconciliation",
            "Reconciliation",
            List.of("dashboard", "reconciliation", "notifications")
    );

    private final String roleId;
    private final String displayName;
    private final List<String> permissions;

    BankRole(String roleId, String displayName, List<String> permissions) {
        this.roleId = roleId;
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public static Optional<BankRole> fromString(String val) {
        if (val == null || val.isBlank()) {
            return Optional.empty();
        }
        String clean = val.trim().toLowerCase().replace("_", "-").replace(" ", "-");
        if (clean.startsWith("role-")) {
            clean = clean.substring(5);
        }
        for (BankRole role : values()) {
            if (role.roleId.equalsIgnoreCase(clean)
                    || role.name().equalsIgnoreCase(val.trim())
                    || role.displayName.equalsIgnoreCase(val.trim())) {
                return Optional.of(role);
            }
        }
        if (clean.contains("admin")) return Optional.of(BANK_ADMIN);
        if (clean.contains("oper")) return Optional.of(BANK_OPERATIONS);
        if (clean.contains("finan")) return Optional.of(BANK_FINANCE);
        if (clean.contains("recon")) return Optional.of(BANK_RECONCILIATION);
        return Optional.empty();
    }
}
