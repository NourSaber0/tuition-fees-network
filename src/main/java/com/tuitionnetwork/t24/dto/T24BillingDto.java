package com.tuitionnetwork.t24.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class T24BillingDto {

    public record RetrieveBillingRequest(
            String nationalId,
            String accountNumber
    ) {}

    public record BillingItem(
            String billingId,
            String institutionCode,
            String institutionName,
            String studentNationalId,
            String studentName,
            String feeType,
            String academicPeriod,
            BigDecimal originalAmount,
            BigDecimal paidAmount,
            BigDecimal remainingAmount,
            LocalDate dueDate,
            String status
    ) {}

    public record RetrieveBillingResponse(
            String status,
            String customerNumber,
            String accountNumber,
            String customerName,
            List<BillingItem> items,
            BigDecimal totalOutstanding,
            String message
    ) {}

    public record RequestBillingRequest(
            String institutionCode,
            String studentNationalId,
            String studentName,
            String feeType,
            BigDecimal amount,
            String currency,
            String academicPeriod,
            LocalDate dueDate
    ) {}

    public record RequestBillingResponse(
            String status,
            String billingId,
            String message
    ) {}

    public record UpdateBillingRequest(
            String billingId,
            BigDecimal amountPaid,
            String paymentMethod,
            String transactionReference,
            BigDecimal newRemainingAmount
    ) {
        public UpdateBillingRequest(String billingId, BigDecimal amountPaid, String transactionReference) {
            this(billingId, amountPaid, "CIB_DEBIT", transactionReference, null);
        }
    }

    public record UpdateBillingResponse(
            String status,
            String billingId,
            BigDecimal newRemainingAmount,
            String message
    ) {}
}
