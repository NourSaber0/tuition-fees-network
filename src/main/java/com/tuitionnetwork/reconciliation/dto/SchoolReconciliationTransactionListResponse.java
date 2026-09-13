package com.tuitionnetwork.reconciliation.dto;

import java.util.List;

public record SchoolReconciliationTransactionListResponse(
        List<SchoolReconciliationTransactionDto> data,
        long total,
        int page,
        int pageSize,
        int totalPages
) {}
