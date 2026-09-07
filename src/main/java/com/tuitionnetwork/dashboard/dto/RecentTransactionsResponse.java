package com.tuitionnetwork.dashboard.dto;

import java.util.List;

public record RecentTransactionsResponse(
        List<TransactionSummaryDto> data
) {
}
