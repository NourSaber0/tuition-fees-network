package com.tuitionnetwork.reporting.dto;

import java.util.UUID;

/** Optional context filters carried on {@link GenerateReportRequest}. Any field may be null. */
public record ReportFilters(
        UUID institutionId,
        String feeType,
        String paymentStatus,
        String paymentMethod,
        Integer eppTenor,
        String eppStatus,
        String reconStatus
) {
    public static ReportFilters empty() {
        return new ReportFilters(null, null, null, null, null, null, null);
    }
}
