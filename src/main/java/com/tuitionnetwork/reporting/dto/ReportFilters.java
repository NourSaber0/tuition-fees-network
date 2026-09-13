package com.tuitionnetwork.reporting.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Optional context filters carried on {@link GenerateReportRequest}. Any field may be null. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportFilters(
        UUID institutionId,
        @JsonAlias("feeCategory") String feeType,
        String paymentStatus,
        String paymentMethod,
        Integer eppTenor,
        String eppStatus,
        String reconStatus
) {
    public static ReportFilters empty() {
        return new ReportFilters(null, null, null, null, null, null, null);
    }

    public ReportFilters withInstitutionId(UUID id) {
        return new ReportFilters(id, feeType, paymentStatus, paymentMethod, eppTenor, eppStatus, reconStatus);
    }
}
