package com.tuitionnetwork.reconciliation.dto;

import com.tuitionnetwork.identity.dto.InstitutionSettlementDto;
import com.tuitionnetwork.identity.dto.InstitutionSettlementSummaryDto;

import java.util.List;

public record SchoolSettlementListResponse(
        List<InstitutionSettlementDto> data,
        long total,
        int page,
        int pageSize,
        int totalPages,
        InstitutionSettlementSummaryDto summary
) {}
