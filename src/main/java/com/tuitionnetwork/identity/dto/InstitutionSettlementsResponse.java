package com.tuitionnetwork.identity.dto;

import java.util.List;

public record InstitutionSettlementsResponse(
        InstitutionSettlementSummaryDto summary,
        List<InstitutionSettlementDto> data
) {}
