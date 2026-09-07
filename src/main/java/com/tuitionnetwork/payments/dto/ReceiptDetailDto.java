package com.tuitionnetwork.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceiptDetailDto(
        UUID receiptId,
        UUID paymentId,
        String receiptReference,
        BigDecimal amount,
        String currency,
        String cryptoSignature,
        String fileUrl,
        LocalDateTime issuedAt
) {
}
