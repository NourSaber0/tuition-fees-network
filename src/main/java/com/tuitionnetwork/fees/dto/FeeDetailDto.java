package com.tuitionnetwork.fees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FeeDetailDto(
        String id,
        String studentId,
        String studentName,
        String studentRef,
        String grade,
        String name,
        String category,
        String term,
        LocalDate dueDate,
        BigDecimal originalAmountEGP,
        BigDecimal paidEGP,
        BigDecimal remainingEGP,
        String status,
        FeeOverdueInfoDto overdue,
        FeePenaltySnapshotDto penalty,
        List<FeePaymentHistoryDto> paymentHistory
) {
}
