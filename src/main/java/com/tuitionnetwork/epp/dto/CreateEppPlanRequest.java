package com.tuitionnetwork.epp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateEppPlanRequest(
        String cardToken,
        String studentName,
        String nationalId,
        String institution,
        String feeDescription,
        BigDecimal principalEGP,
        Integer tenor,
        UUID sourcePaymentId
) {
}
