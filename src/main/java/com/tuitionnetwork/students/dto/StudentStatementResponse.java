package com.tuitionnetwork.students.dto;

import java.math.BigDecimal;
import java.util.List;

public record StudentStatementResponse(
        String studentId,
        String studentRef,
        String studentName,
        String schoolName,
        String grade,
        String generatedAt,
        BigDecimal totalInvoicedEGP,
        BigDecimal totalPaidEGP,
        BigDecimal currentBalanceEGP,
        List<StatementTransactionDto> ledger
) {
}
