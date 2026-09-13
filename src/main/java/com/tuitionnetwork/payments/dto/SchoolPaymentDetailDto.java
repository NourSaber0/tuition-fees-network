package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.util.List;

public record SchoolPaymentDetailDto(
        String id,
        String studentId,
        String studentName,
        BigDecimal amountEGP,
        String currency,
        String method,
        String date,
        String time,
        String status,
        String reconciliation,
        List<SchoolPaymentAllocationItemDto> allocation
) {
}
