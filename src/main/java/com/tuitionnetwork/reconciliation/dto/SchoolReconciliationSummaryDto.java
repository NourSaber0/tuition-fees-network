package com.tuitionnetwork.reconciliation.dto;

import java.time.LocalDate;

public record SchoolReconciliationSummaryDto(
        long totalReconciled,
        long totalUnreconciled,
        long totalPending,
        long grossCollectedEGP,
        long cibFeeEGP,
        long netSettledEGP,
        long pendingPayoutEGP,
        Long grossCollected,
        Long cibFee,
        Long netSettled,
        Long pendingPayout,
        LocalDate lastSettlementDate
) {
    public SchoolReconciliationSummaryDto(
            long totalReconciled,
            long totalUnreconciled,
            long totalPending,
            long grossCollectedEGP,
            long cibFeeEGP,
            long netSettledEGP,
            long pendingPayoutEGP,
            LocalDate lastSettlementDate
    ) {
        this(
                totalReconciled,
                totalUnreconciled,
                totalPending,
                grossCollectedEGP,
                cibFeeEGP,
                netSettledEGP,
                pendingPayoutEGP,
                grossCollectedEGP,
                cibFeeEGP,
                netSettledEGP,
                pendingPayoutEGP,
                lastSettlementDate
        );
    }
}
