package com.tuitionnetwork.identity.dto;

import java.time.LocalDate;

public record InstitutionSettlementSummaryDto(
        long totalSettledEGP,
        int recordCount,
        LocalDate lastSettlementDate
) {}
