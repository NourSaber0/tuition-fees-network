package com.tuitionnetwork.reconciliation.dto;

public record ReconciliationSummaryDto(
        long totalTransactions,
        long matched,
        long pending,
        long exceptions,
        long totalRuns,
        long totalExceptions,
        long pendingExceptions
) {
    public ReconciliationSummaryDto(long totalTransactions, long matched, long pending, long exceptions) {
        this(totalTransactions, matched, pending, exceptions, 0L, exceptions, pending);
    }
}
