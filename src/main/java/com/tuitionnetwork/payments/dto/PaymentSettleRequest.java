package com.tuitionnetwork.payments.dto;

import com.tuitionnetwork.payments.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentSettleRequest(
        String idempotencyKey,
        UUID guardianId,
        PaymentMethod paymentMethod,
        List<SelectedDueDto> selectedDues,
        BigDecimal totalAmount,
        EppSelectionDto eppSelection,
        String cardNumber,
        String cibAccountNumber
) {
}
